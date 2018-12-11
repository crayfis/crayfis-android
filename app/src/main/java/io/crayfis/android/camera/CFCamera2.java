package io.crayfis.android.camera;

import android.Manifest;
import android.annotation.TargetApi;
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
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.TonemapCurve;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.R;
import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.util.CFLog;


/**
 * Created by Jeff on 8/31/2017.
 */

@TargetApi(21)
class CFCamera2 extends CFCamera {

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice mCameraDevice;
    private CameraCharacteristics mCameraCharacteristics;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private Size mPreviewSize;
    private CameraCaptureSession mCaptureSession;

    private Allocation ain;
    private static final int YUV_FORMAT = ImageFormat.YUV_420_888;
    
    private AtomicInteger mBuffersQueued = new AtomicInteger();
    private final ArrayDeque<TotalCaptureResult> mQueuedCaptureResults = new ArrayDeque<>();

    private ConfiguredCallback mConfiguredCallback;

    /**
     * Callback for opening the camera
     */
    private CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;

            try {

                configureCameraPreviewSession();
                RCF_BUILDER.setCamera2(ain, mApplication.getRenderScript());

                ain.setOnBufferAvailableListener(mOnBufferAvailableListener);
                Surface surface = ain.getSurface();
                mPreviewRequestBuilder.addTarget(surface);
                mCameraDevice.createCaptureSession(Collections.singletonList(surface), mStateCallback, null);

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
    private CameraCaptureSession.StateCallback mStateCallback = new CameraCaptureSession.StateCallback() {

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

                mPreviewRequest = mPreviewRequestBuilder.build();
                // Finally, we start displaying the camera preview.
                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mCameraHandler);
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
            changeCamera();
        }
    };
    

    /**
     * Callback for buffers added to queue
     */
    private Allocation.OnBufferAvailableListener mOnBufferAvailableListener
            = new Allocation.OnBufferAvailableListener() {
        @Override
        public void onBufferAvailable(Allocation a) {
            mBuffersQueued.incrementAndGet();
            createFrames();
        }
    };

    /**
     * Callback for captured frames
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback =  new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            super.onCaptureCompleted(session, request, result);

            mQueuedCaptureResults.add(result);
            createFrames();
        }
    };

    /**
     * If timestamp/buffer pairs are available, use to create RawCameraFrame and send to
     * RawCameraFrame.Callback in a HandlerThread
     */
    private void createFrames() {

        // make sure the timestamp queues aren't cleared underneath us
        synchronized (mTimestampHistory) {

            if(mBuffersQueued.intValue() > 0 && !mQueuedCaptureResults.isEmpty()) {
                final RawCameraFrame frame = RCF_BUILDER.setAcquisitionTime(new AcquisitionTime())
                        .setCaptureResult(mQueuedCaptureResults.poll())
                        .build();

                mTimestampHistory.addValue(frame.getAcquiredTimeNano());
                mFrameHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        frame.commit();
                    }
                });
                mBuffersQueued.decrementAndGet();

            }
        }
    }

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
        mPreviewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,
                mCameraCharacteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY));
        mPreviewRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF);

        // get rid of larger results we don't need
        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE, false);

    }

    private long findTargetDuration(Size size) {

        // go through ranges known to be supported
        Long requestedDuration = 33000000L;
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

        StreamConfigurationMap map = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        // now make sure this is above the minimum for the format
        long minDuration = map.getOutputMinFrameDuration(YUV_FORMAT, size);

        CFLog.d("requestedDuration = " + requestedDuration);
        CFLog.d("Target FPS = " + (int)(1000000000./requestedDuration));

        return Math.max(requestedDuration, minDuration);
    }


    /**
     * Configures and starts CameraCaptureSession with a CaptureRequest.Builder
     */
    private void configureCameraPreviewSession() throws CameraAccessException {


        // We set up a CaptureRequest.Builder with the output Surface.
        mPreviewRequestBuilder
                = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        // make preview as close to RAW as possible
        configureManualSettings();

        mPreviewSize = CONFIG.getTargetResolution().getClosestSize(mCameraCharacteristics);
        mResX = mPreviewSize.getWidth();
        mResY = mPreviewSize.getHeight();

        long requestedDuration = findTargetDuration(mPreviewSize);

        // set the frame length and exposure time
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, requestedDuration);

        Range<Long> exposureTimes = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if(exposureTimes != null) {
            // allow 1 ns/pix dead time for internal processing
            long requestedExpTime = (long) (Math.min(exposureTimes.getUpper(), requestedDuration)
                    * (1-CONFIG.getFracDeadTime()));

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, requestedExpTime);
        }

        ain = Allocation.createTyped(mRS, new Type.Builder(mRS, Element.YUV(mRS))
                .setX(mPreviewSize.getWidth())
                .setY(mPreviewSize.getHeight())
                .setYuvFormat(YUV_FORMAT)
                .create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
    }
    
    @Override
    void configure(ConfiguredCallback callback) {

        mConfiguredCallback = callback;

        synchronized (mQueuedCaptureResults) {
            mQueuedCaptureResults.clear();
        }

        // get rid of old camera setup

        mCameraOpenCloseLock.acquireUninterruptibly();

        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != ain) {
            ain.destroy();
            ain = null;
        }
        mPreviewSize = null;
        mCameraOpenCloseLock.release();

        if(mCameraId == -1) {
            mConfiguredCallback.onConfigured();
            return;
        }

        // make sure we have permission to use the camera
        if (ContextCompat.checkSelfPermission(mApplication, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            mApplication.userErrorMessage(true, R.string.quit_permission);
            return;
        }

        // open the camera in the thread we have created
        CameraManager manager = (CameraManager) mApplication.getSystemService(Context.CAMERA_SERVICE);

        try {
            mCameraOpenCloseLock.acquireUninterruptibly();

            String[] idList = manager.getCameraIdList();
            String idString = idList[mCameraId];

            mCameraCharacteristics = manager.getCameraCharacteristics(idString);
            manager.openCamera(idString, mCameraDeviceCallback, mCameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
            mCameraOpenCloseLock.release();
            mApplication.userErrorMessage(true, R.string.camera_error, 100 + e.getReason());
        }
    }

    @Override
    public void changeDataRate(boolean increase) {
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

    @Override
    int getNumberOfCameras() {
        CameraManager cameraManager = (CameraManager) mApplication.getSystemService(Context.CAMERA_SERVICE);
        try {
            return cameraManager.getCameraIdList().length;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Boolean isFacingBack() {
        if(mCameraCharacteristics == null) return null;
        Integer lensFacing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if(lensFacing != null && mCameraId != -1) {
            return lensFacing == CameraMetadata.LENS_FACING_BACK;
        }
        return null;
    }

    @Override
    public String getParams() {

        if(mPreviewSize == null) return "";
        StringBuilder paramtxt = new StringBuilder("Size: " + mPreviewSize.toString() + ", ");

        if(mPreviewRequest != null) {
            for (CaptureRequest.Key<?> k : mPreviewRequest.getKeys()) {
                paramtxt.append(k.getName())
                        .append(": ")
                        .append((mPreviewRequest.get(k) != null ?
                        mPreviewRequest.get(k).toString() : "null"))
                        .append(", ");
            }
        }
        return paramtxt.toString();
    }

    @Override
    public String getStatus() {
        String devtxt = "Camera API: Camera2 \n";
        if (mPreviewSize != null) {
            ResolutionSpec targetRes = CONFIG.getTargetResolution();

            devtxt += "Image dimensions = " + mPreviewSize.toString()
                    + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) + ")\n";
        }
        return devtxt + super.getStatus();
    }

}
