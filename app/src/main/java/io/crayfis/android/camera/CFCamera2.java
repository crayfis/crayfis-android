package io.crayfis.android.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.R;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.util.CFLog;


/**
 * Created by Jeff on 8/31/2017.
 */

@TargetApi(21)
class CFCamera2 extends CFCamera {

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    protected CameraDevice mCameraDevice;
    protected CameraCharacteristics mCameraCharacteristics;

    protected CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    protected Size mPreviewSize;
    private CameraCaptureSession mCaptureSession;

    protected Allocation ain;
    
    private AtomicInteger mBuffersQueued = new AtomicInteger();
    private final ArrayDeque<Long> mQueuedTimestamps = new ArrayDeque<>();


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
                RCF_BUILDER.setCamera2(mCameraCharacteristics, mCameraId, ain, mApplication.getRenderScript());

                ain.setOnBufferAvailableListener(mOnBufferAvailableListener);
                Surface surface = ain.getSurface();
                mPreviewRequestBuilder.addTarget(surface);
                mCameraDevice.createCaptureSession(Arrays.asList(surface), mStateCallback, null);

            } catch (CameraAccessException e) {
                e.printStackTrace();
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
            CFLog.e("Error = " + error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mApplication.userErrorMessage(R.string.camera_error, true);
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

            // When the session is ready, we start displaying the preview.
            mCaptureSession = cameraCaptureSession;
            try {

                mPreviewRequest = mPreviewRequestBuilder.build();
                // Finally, we start displaying the camera preview.
                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                // camera was already closed
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

            //CFLog.d("ExposureTime = " + result.get(CaptureResult.SENSOR_EXPOSURE_TIME));

            mQueuedTimestamps.add(result.get(CaptureResult.SENSOR_TIMESTAMP));
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

            if(mBuffersQueued.intValue() > 0 && !mQueuedTimestamps.isEmpty()) {
                final RawCameraFrame frame = RCF_BUILDER.setAcquisitionTime(new AcquisitionTime())
                        .setTimestamp(mQueuedTimestamps.poll())
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

    protected void configureManualSettings() {

        mPreviewRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, true);

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF);

        //TODO: More color correction?

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

    protected long findTargetDuration(Size size) {

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

        // first, check whether RAW format is requested/available

        StreamConfigurationMap map = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        String formatString = "Supported formats: ";
        for (int i : map.getOutputFormats()) {
            formatString += i + ", ";
        }
        CFLog.d(formatString);

        int format = ImageFormat.YUV_420_888;

        if(CONFIG.getTargetResolution().name.equalsIgnoreCase("RAW")) {
            for (int f : map.getOutputFormats()) {
                if (f == ImageFormat.RAW_SENSOR) {
                    format = ImageFormat.RAW_SENSOR;
                    break;
                }
            }
        }

        // now make sure this is above the minimum for the format
        long minDuration = map.getOutputMinFrameDuration(format, size);

        CFLog.d("Target FPS = " + (int)(1000000000./requestedDuration));

        return Math.max(requestedDuration, minDuration);
    }


    /**
     * Configures and starts CameraCaptureSession with a CaptureRequest.Builder
     */
    protected void configureCameraPreviewSession() throws CameraAccessException {


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
            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                    Math.min(exposureTimes.getUpper(), requestedDuration));
        }

        ain = Allocation.createTyped(mRS, new Type.Builder(mRS, Element.YUV(mRS))
                .setX(mPreviewSize.getWidth())
                .setY(mPreviewSize.getHeight())
                .setYuvFormat(ImageFormat.YUV_420_888)
                .create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
    }
    
    @Override
    void configure() {

        synchronized (mQueuedTimestamps) {
            mQueuedTimestamps.clear();
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
            return;
        }

        // make sure we have permission to use the camera
        if (ContextCompat.checkSelfPermission(mApplication, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            mApplication.userErrorMessage(R.string.quit_permission, true);
            return;
        }

        // open the camera in the thread we have created
        CameraManager manager = (CameraManager) mApplication.getSystemService(Context.CAMERA_SERVICE);

        try {
            mCameraOpenCloseLock.tryAcquire();

            String[] idList = manager.getCameraIdList();
            String idString = idList[mCameraId];

            mCameraCharacteristics = manager.getCameraCharacteristics(idString);

            manager.openCamera(idString, mCameraDeviceCallback, mCameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
            mCameraOpenCloseLock.release();
            mApplication.userErrorMessage(R.string.camera_error, true);
        }
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
