package io.crayfis.android.daq;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.TonemapCurve;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import android.renderscript.RenderScript;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import io.crayfis.android.R;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.FrameHistory;


/**
 * Created by Jeff on 8/31/2017.
 */

class CFCamera {

    // workable RAW formats with SDK 21
    public static final Set<Integer> RAW_FORMATS = new HashSet<>
            (Arrays.asList(ImageFormat.RAW10,
                    ImageFormat.RAW12,
                    ImageFormat.RAW_SENSOR));

    private final Frame.Builder FRAME_BUILDER;
    private final CFConfig CONFIG;
    
    private CFApplication mApplication;

    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private StreamConfigurationMap mConfigMap;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private List<CaptureRequest> mPreviewRequests = new ArrayList<>();
    private Size mPreviewSize;
    private CameraCaptureSession mCaptureSession;

    // thread for Camera callbacks
    protected Handler mCameraHandler;
    protected final HandlerThread mCameraThread = new HandlerThread("CFCamera");

    // thread for receiving buffers and CaptureResults
    protected Handler mResultHandler;
    protected final HandlerThread mResultThread = new HandlerThread("Result");

    int mCameraId = -1;
    int mResX;
    int mResY;
    int mFormat;

    // CaptureCallback depending on RAW/YUV format
    private Frame.Producer mFrameProducer;
    private final FrameHistory<Long> mTimestampHistory = new FrameHistory<>(100);

    interface ConfiguredCallback {
        void onConfigured();
    }

    private ConfiguredCallback mConfiguredCallback;

    CFCamera(Frame.Builder builder) {
        FRAME_BUILDER = builder;
        CONFIG = CFConfig.getInstance();
    }

    void register(Context context) {
        mApplication = (CFApplication)context.getApplicationContext();
        mCameraManager = (CameraManager) mApplication.getSystemService(Context.CAMERA_SERVICE);

        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mResultThread.start();
        mResultHandler = new Handler(mResultThread.getLooper());
    }

    void unregister() {

        changeCameraFrom(mCameraId);

        // now get rid of the threads
        mCameraThread.quitSafely();
        mResultThread.quitSafely();
        try {
            mCameraThread.join(500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mResultThread.join(500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    synchronized void changeCameraFrom(final int currentId) {

        if (currentId != mCameraId || !mCameraThread.isAlive()) return;

        synchronized (mTimestampHistory) {
            mTimestampHistory.clear();
        }
        CFConfig.getInstance().setPrecalConfig(null);

        // find next camera based on DAQ state
        int nextId = -1;
        CFApplication.State state = mApplication.getApplicationState();

        switch (state) {
            case INIT:
                nextId = 0;
                break;
            case SURVEY:
                // switch cameras and try again
                nextId = currentId + 1;
                if (nextId >= getNumberOfCameras()) {
                    nextId = -1;
                } else {
                    // need a new SURVEY XB for next camera
                    ExposureBlockManager.getInstance().abortExposureBlock();
                }
                break;
            case PRECALIBRATION:
            case CALIBRATION:
            case DATA:
            case IDLE:
            case FINISHED:
                // take a break for a while
        }

        if (nextId == -1 && state != CFApplication.State.IDLE && state != CFApplication.State.FINISHED) {
            mApplication.startInitTimer();
        }

        mCameraId = nextId;

        CFLog.i("cameraId:" + currentId + " -> " + nextId);

        configure(new ConfiguredCallback() {
            @Override
            public void onConfigured() {
                if(state == CFApplication.State.INIT) {
                    mApplication.setApplicationState(CFApplication.State.SURVEY);
                }
            }
        });

    }

    /**
     * Callback for opening the camera
     */
    private final CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;

            try {

                mPreviewRequests.clear();
                configureCameraPreviewSession();

                boolean raw = (RAW_FORMATS.contains(mFormat));
                mFrameProducer = Frame.Producer.create(raw, mApplication.getRenderScript(),
                        mPreviewSize, mFrameCallback, mResultHandler, FRAME_BUILDER);

                RenderScript rs = mApplication.getRenderScript();
                if(raw) FRAME_BUILDER.configureRAW(rs, mPreviewSize, mFrameProducer);
                else FRAME_BUILDER.configureYUV(rs, mPreviewSize, mFrameProducer);

                List<Surface> outputs = mFrameProducer.getSurfaces();
                for(Surface s: outputs) {
                    mPreviewRequestBuilder.addTarget(s);
                    mPreviewRequests.add(mPreviewRequestBuilder.build());
                    mPreviewRequestBuilder.removeTarget(s); // just one per request
                }


                mCameraDevice.createCaptureSession(outputs, mStateCallback, mCameraHandler);

            } catch (CameraAccessException e) {
                mApplication.userErrorMessage(true, R.string.camera_error, 100 + e.getReason());
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            // If the user wants the camera for a different app, better
            // to quit than to risk errors
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mApplication.finishAndQuit(R.string.quit_no_cameras);
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mApplication.userErrorMessage(true, R.string.camera_error, error);
        }
    };

    /**
     * Callback for creating a CameraCaptureSession
     */
    private final CameraCaptureSession.StateCallback mStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

            // The camera is already closed
            if (null == mCameraDevice) {
                return;
            }

            mConfiguredCallback.onConfigured();

            // When the session is ready, we start displaying the preview.
            mCaptureSession = cameraCaptureSession;
            try {
                // Finally, we start displaying the camera preview.
                if(mPreviewRequests.size() == 1) {
                    mCaptureSession.setRepeatingRequest(mPreviewRequests.get(0), mFrameProducer, mResultHandler);
                } else {
                    mCaptureSession.setRepeatingBurst(mPreviewRequests, mFrameProducer, mResultHandler);
                }
            } catch (CameraAccessException e) {
                mApplication.userErrorMessage(true, R.string.camera_error, 100 + e.getReason());
            } catch (IllegalStateException e) {
                // camera was already closed
                mApplication.userErrorMessage(true, R.string.camera_error, 200);
            }
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            CFLog.e("Configure failed");
            changeCameraFrom(mCameraId);
        }
    };


    private final Frame.OnFrameCallback mFrameCallback = new Frame.OnFrameCallback() {
        @Override
        public void onFrame(Frame frame) {
            // make sure the timestamp queues aren't cleared underneath us
            synchronized (mTimestampHistory) {
                mTimestampHistory.addValue(frame.getAcquiredTimeNano());
            }
            frame.commit();
        }

        @Override
        public void onDropped() {
            CFLog.w("dropped frame");
            ExposureBlock xb = ExposureBlockManager.getInstance().getCurrentExposureBlock();
            if(xb != null)
                xb.dropped.incrementAndGet();

            // TODO: should we lower the data rate if there are too many drops?
        }
    };

    private void configureManualSettings() {

        mPreviewRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, true);

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF);

        //TODO: RggbChannelVector?

        mPreviewRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE);
        float[] curve = new float[] {0, 0, 1, 1}; // linear response
        mPreviewRequestBuilder.set(CaptureRequest.TONEMAP_CURVE, new TonemapCurve(curve, curve, curve));
        mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, new ColorSpaceTransform(new int[]{
                1, 1, 0, 1, 0, 1,
                0, 1, 1, 1, 0, 1,
                0, 1, 0, 1, 1, 1,
                }) // identity RGB -> sRGB transform
        );


        mPreviewRequestBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,
                mCameraCharacteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY));
        mPreviewRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF);

        // get rid of larger results we don't need
        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE, false);

    }


    /**
     * Configures and starts CameraCaptureSession with a CaptureRequest.Builder
     */
    private void configureCameraPreviewSession() throws CameraAccessException {


        // We set up a CaptureRequest.Builder with the output Surface.
        mPreviewRequestBuilder
                = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        ResolutionSpec resolutionSpec = CONFIG.getTargetResolution();

        // make preview as close to RAW as possible
        configureManualSettings();

        if(resolutionSpec.name.equals(ResolutionSpec.RAW)) {
            mFormat = findRAWFormat();
            if(mFormat == ImageFormat.YUV_420_888) {
                // no RAW capabilities, so switch to MAX
                resolutionSpec = ResolutionSpec.fromString("MAX");
                CONFIG.setTargetResolution(resolutionSpec);
            }
        } else {
            mFormat = ImageFormat.YUV_420_888;
        }


        mPreviewSize = getClosestSize(mConfigMap, resolutionSpec, mFormat);
        long requestedDuration = findTargetDuration();

        mResX = mPreviewSize.getWidth();
        mResY = mPreviewSize.getHeight();


        // set the frame length and exposure time
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, requestedDuration);

        Range<Long> exposureTimes = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if(exposureTimes != null) {
            // allow 1 ns/pix dead time for internal processing
            long requestedExpTime = (long) (Math.min(exposureTimes.getUpper(), requestedDuration)
                    * (1-CONFIG.getFracDeadTime()));

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, requestedExpTime);
        }

    }

    private int findRAWFormat() {

        // check RAW capabilities
        int[] caps = mCameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if(caps == null) return ImageFormat.YUV_420_888;

        boolean rawCaps = false;
        for(int i : caps) {
            if (i == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) {
                rawCaps = true;
                break;
            }
        }
        if(!rawCaps) return ImageFormat.YUV_420_888;

        for (int fmt : mConfigMap.getOutputFormats()) {
            if(RAW_FORMATS.contains(fmt)) return fmt;
        }

        return ImageFormat.YUV_420_888;
    }

    private static Size getClosestSize(@NonNull StreamConfigurationMap map, ResolutionSpec res, int format) {

        Size[] outputSizes = map.getOutputSizes(format);
        if (outputSizes == null) return null;

        List<Size> availableSizes = Arrays.asList(outputSizes);
        // sort to match the total # of pixels in the requested spec.
        final int targetArea = res.width*res.height;
        Collections.sort(availableSizes, new Comparator<Size>() {
            @Override
            public int compare(Size s0, Size s1) {
                return Math.abs(targetArea - s0.getWidth()*s0.getHeight())
                        - Math.abs(targetArea - s1.getWidth()*s1.getHeight());
            }
        });

        // return the elt with the smallest difference from the requested # of pixels.
        return availableSizes.get(0);


    }

    private long findTargetDuration() {

        // go through ranges known to be supported
        long requestedDuration = 33000000L;
        Range<Integer>[] availableFpsRanges
                = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

        // fps should be as close to target as possible, but keep at max for long exposure
        if(availableFpsRanges != null) {

            // find closest number in given ranges to target
            long targetDuration = CONFIG.getTargetFPS() == 0 ? 5000000000L : (long)(1000000000L / CONFIG.getTargetFPS());
            for (Range<Integer> r : availableFpsRanges) {
                long maxDuration = 1000000000L / r.getLower();
                long minDuration = 1000000000L / r.getUpper();
                if (targetDuration >= minDuration && targetDuration <= maxDuration) {
                    requestedDuration = targetDuration;
                    break;
                }

                if (minDuration > targetDuration
                        && Math.abs(targetDuration - requestedDuration) > Math.abs(targetDuration - minDuration)) {
                    requestedDuration = minDuration;
                } else if (Math.abs(targetDuration - requestedDuration) > Math.abs(targetDuration - maxDuration)) {
                    requestedDuration = maxDuration;
                }
            }
        }

        // seems to work better when rounded to ms
        requestedDuration = 1000000L * Math.round(requestedDuration/1000000.);

        // now make sure this is above the minimum for the format
        long minDuration = mConfigMap.getOutputMinFrameDuration(mFormat, mPreviewSize);

        CFLog.d("requestedDuration = " + requestedDuration);
        CFLog.d("Target FPS = " + (1000000000./requestedDuration));

        return Math.max(requestedDuration, minDuration);
    }

    void configure(ConfiguredCallback callback) {

        mConfiguredCallback = callback;

        // get rid of old camera setup
        mCameraOpenCloseLock.acquireUninterruptibly();

        // since these are configured with mCameraHandler, close them in
        // the main thread to avoid a race condition with unregister()
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mFrameProducer !=  null) {
            mFrameProducer.close();
            mFrameProducer = null;
        }
        mPreviewSize = null;
        mCameraOpenCloseLock.release();

        if(mCameraId == -1) {
            return;
        }

        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {

                // make sure we have permission to use the camera
                if (ContextCompat.checkSelfPermission(mApplication, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    mApplication.userErrorMessage(true, R.string.quit_permission);
                    return;
                }

                // open the camera in the thread we have created
                try {
                    mCameraOpenCloseLock.acquireUninterruptibly();

                    String[] idList = mCameraManager.getCameraIdList();
                    String idString = idList[mCameraId];

                    mCameraCharacteristics = mCameraManager.getCameraCharacteristics(idString);
                    mConfigMap = mCameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    mCameraManager.openCamera(idString, mCameraDeviceCallback, mCameraHandler);

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    mCameraOpenCloseLock.release();
                    mApplication.userErrorMessage(true, R.string.camera_error, 100 + e.getReason());
                }
            }
        });

    }

    void changeDataRate(boolean increase) {
        // do nothing if we're locked
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApplication);
        if(prefs.getBoolean(mApplication.getString(R.string.prefFPSResLock), false)) {
            return;
        }

        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(map != null) {
            Size[] outputSizes = map.getOutputSizes(Allocation.class);
            List<Size> sizes = Arrays.asList(outputSizes);

            Collections.sort(sizes, new Comparator<Size>() {
                @Override
                public int compare(Size s0, Size s1) {
                    return s0.getWidth() * s0.getHeight() - s1.getWidth() * s1.getHeight();
                }
            });

            int index = sizes.indexOf(mPreviewSize);
            if(increase && index < sizes.size()-1) {
                index++;
            } else if(!increase && index > 0) {
                index--;
            } else {
                return;
            }

            Size newSize = sizes.get(index);
            CONFIG.setTargetResolution(newSize.getWidth(), newSize.getHeight());
        }
    }

    boolean isStreamingRAW() {
        return RAW_FORMATS.contains(mFormat);
    }

    int getCameraId() {
        return mCameraId;
    }

    int getNumberOfCameras() {
        try {
            return mCameraManager.getCameraIdList().length;
        } catch (Exception e) {
            return 0;
        }
    }

    int getResX() {
        return mResX;
    }

    int getResY() {
        return mResY;
    }

    /**
     * Calculates and returns the average FPS of the last 100 frames produced
     * @return double
     */
    double getFPS() {
        synchronized(mTimestampHistory) {
            int nframes = mTimestampHistory.size();
            if (nframes>0) {
                return ((double) nframes) / (System.nanoTime() - mTimestampHistory.getOldest()) * 1000000000L;
            }
        }

        return 0.0;
    }

    Boolean isFacingBack() {
        if(mCameraCharacteristics == null) return null;
        Integer lensFacing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if(lensFacing != null && mCameraId != -1) {
            return lensFacing == CameraMetadata.LENS_FACING_BACK;
        }
        return null;
    }

    String getParams() {

        if(mPreviewSize == null) return "";
        StringBuilder paramtxt = new StringBuilder("Size: " + mPreviewSize.toString() + ", ");

        if(mPreviewRequests.size() > 0) {
            CaptureRequest request = mPreviewRequests.get(0);
            for (CaptureRequest.Key<?> k : request.getKeys()) {
                paramtxt.append(k.getName())
                        .append(": ")
                        .append((request.get(k) != null ?
                        request.get(k).toString() : "null"))
                        .append(", ");
            }
        }
        return paramtxt.toString();
    }

    String getStatus() {
        String devtxt = "Camera:\n";
        ResolutionSpec targetRes = CONFIG.getTargetResolution();
        devtxt += "Image dimensions = " + (mPreviewSize == null ? "0x0" : mPreviewSize.toString())
                + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) + ")\n";


        devtxt += "Camera ID: " + mCameraId + ", FPS = " + String.format("%.02f", getFPS())
                + "(" + CONFIG.getTargetFPS() + ")\n";

        return devtxt;
    }

}
