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
import android.os.Handler;
import android.os.HandlerThread;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.R;
import io.crayfis.android.util.CFLog;


/**
 * Created by Jeff on 8/31/2017.
 */

@TargetApi(21)
class CFCamera2 extends CFCamera {

    private final RawCamera2Frame.Builder RCF_BUILDER;

    // thread for Camera callbacks
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    // thread for posting jobs handling frames
    private Handler mFrameHandler;
    private HandlerThread mFrameThread;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice mCameraDevice;
    private CameraCharacteristics mCameraCharacteristics;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private Size mPreviewSize;
    private CameraCaptureSession mCaptureSession;

    private Allocation ain;
    private AtomicInteger mBuffersQueued = new AtomicInteger();
    private final ArrayDeque<Long> mQueuedTimestamps = new ArrayDeque<>();


    CFCamera2() {
        super();
        RCF_BUILDER = new RawCamera2Frame.Builder();
    }

    /**
     * Callback for opening the camera
     */
    private CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            mCameraOpenCloseLock.release();
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
     * If timestamp/buffer pairs are available, use to create RawCameraFrame and send to
     * RawCameraFrame.Callback in a HandlerThread
     */
    private void createFrames() {

        // make sure the timestamp queues aren't cleared underneath us
        synchronized (mTimestampHistory) {

            if(mBuffersQueued.intValue() > 0 && !mQueuedTimestamps.isEmpty()) {
                RCF_BUILDER.setAcquisitionTime(new AcquisitionTime())
                        .setTimestamp(mQueuedTimestamps.poll());

                final RawCamera2Frame frame = RCF_BUILDER.build();
                mTimestampHistory.addValue(frame.getAcquiredTimeNano());
                mFrameHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        frame.receiveBytes();
                        mCallback.onRawCameraFrame(frame);
                    }
                });
                mBuffersQueued.decrementAndGet();

            }
        }
    }


    /**
     * Configures and starts CameraCaptureSession with a CaptureRequest.Builder
     */
    private void createCameraPreviewSession() {
        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // make preview as close to RAW as possible

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

            // go through ranges known to be supported
            Long requestedDuration = 33000000L;
            Range<Integer>[] availableFpsRanges
                    = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

            // fps should be as close to target as possible, but keep at max for long exposure
            if(availableFpsRanges != null) {

                // find closest number in given ranges to target
                long targetDuration = CONFIG.getTargetFPS() == 0 ? 5000000000L : 1000000000L / CONFIG.getTargetFPS();
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

            CFLog.d("Target FPS = " + (int)(1000000000./requestedDuration));

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,
                    mCameraCharacteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY));
            mPreviewRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF);

            // set the frame length and exposure time
            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, requestedDuration);

            Range<Long> exposureTimes = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if(exposureTimes != null) {
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                        Math.min(exposureTimes.getUpper(), requestedDuration));
            }

            // get rid of larger results we don't need
            mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE, false);


            // Here, we create a CameraCaptureSession for camera preview.

            Surface asurface = ain.getSurface();
            mPreviewRequestBuilder.addTarget(asurface);
            mCameraDevice.createCaptureSession(Arrays.asList(asurface), mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void changeCameraFrom(int currentId) {

        super.changeCameraFrom(currentId);

        synchronized (mQueuedTimestamps) {
            mQueuedTimestamps.clear();
        }

        // get rid of old camera setup

        try {
            mCameraOpenCloseLock.acquire();
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
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }

        // quit the existing camera thread
        if(mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
                mCameraThread = null;
                mCameraHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        if(mFrameHandler != null) {
            mFrameThread.quitSafely();
            try {
                mFrameThread.join();
                mFrameThread = null;
                mFrameHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // start new threads

        mCameraThread = new HandlerThread("CFCamera2");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mFrameThread = new HandlerThread("RawCamera2Frame");
        mFrameThread.start();
        mFrameHandler = new Handler(mFrameThread.getLooper());


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
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                mApplication.userErrorMessage(R.string.camera_error, true);
            }

            String[] idList = manager.getCameraIdList();
            String idString = idList[mCameraId];

            mCameraCharacteristics = manager.getCameraCharacteristics(idString);

            mPreviewSize = CONFIG.getTargetResolution().getClosestSize(mCameraCharacteristics);

            mResX = mPreviewSize.getWidth();
            mResY = mPreviewSize.getHeight();

            ain = Allocation.createTyped(mRS, new Type.Builder(mRS, Element.YUV(mRS))
                    .setX(mPreviewSize.getWidth())
                    .setY(mPreviewSize.getHeight())
                    .setYuvFormat(ImageFormat.YUV_420_888)
                    .create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

            ain.setOnBufferAvailableListener(mOnBufferAvailableListener);

            RCF_BUILDER.setCamera2(manager, mCameraId, ain, mApplication.getRenderScript());

            manager.openCamera(idString, mCameraDeviceCallback, mCameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }


    @Override
    public String getParams() {

        if(mPreviewSize == null) return "";
        String paramtxt = "Size: " + mPreviewSize.toString() + ", ";

        if(mPreviewRequest != null) {
            for (CaptureRequest.Key<?> k : mPreviewRequest.getKeys()) {
                paramtxt += k.getName() + ": "
                        + (mPreviewRequest.get(k) != null ?
                        mPreviewRequest.get(k).toString() : "null") + ", ";
            }
        }
        return paramtxt;
    }

    @Override
    public String getStatus() {
        String devtxt = "Camera API: Camera2 \n" + super.getStatus();
        if (mPreviewSize != null) {
            ResolutionSpec targetRes = CONFIG.getTargetResolution();

            devtxt += "Image dimensions = " + mPreviewSize.toString()
                    + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) + ")\n";
        }
        return devtxt;
    }

    @Override
    public RawCameraFrame.Builder getFrameBuilder() {
        return RCF_BUILDER;
    }

}