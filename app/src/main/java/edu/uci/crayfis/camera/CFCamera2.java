package edu.uci.crayfis.camera;

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

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;


/**
 * Created by Jeff on 8/31/2017.
 */

@TargetApi(21)
class CFCamera2 extends CFCamera {

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private Handler mFrameHandler;
    private HandlerThread mFrameThread;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice mCameraDevice;
    private CameraCharacteristics mCameraCharacteristics;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private Size mPreviewSize;
    private Range<Integer> mTargetFpsRange = new Range<>(0,0);
    private CameraCaptureSession mCaptureSession;

    private Allocation ain;
    private AtomicInteger mBuffersQueued = new AtomicInteger();


    CFCamera2() {
        super();
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
                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
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
    private synchronized void createFrames() {
        while (mBuffersQueued.intValue() > 0 && !mQueuedTimestamps.isEmpty()) {
            final RawCameraFrame frame = RCF_BUILDER.setAcquisitionTime(new AcquisitionTime())
                    .setTimestamp(mQueuedTimestamps.poll())
                    .build();
            mTimestampHistory.addValue(frame.getAcquiredTimeNano());
            mBuffersQueued.decrementAndGet();
            mFrameHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onRawCameraFrame(frame);
                }
            });
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

            Range<Integer>[] availableFpsRanges
                    = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            String rangeString = "Available ranges: ";
            if(availableFpsRanges != null) {
                for (Range<Integer> r: availableFpsRanges) {
                    rangeString += r.toString() + " ";
                    if((r.getUpper() > mTargetFpsRange.getUpper() || r.getLower() > mTargetFpsRange.getLower())
                            && mTargetFpsRange.getUpper() <= 30) {
                        mTargetFpsRange = r;
                    }
                }
                CFLog.d(rangeString);
                CFLog.d("Target range = " + mTargetFpsRange.toString());
            }

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,
                    mCameraCharacteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY));

            mPreviewRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF);

            Range<Long> exposureTimes = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            Long frameDuration = mPreviewRequestBuilder.get(CaptureRequest.SENSOR_FRAME_DURATION);
            if(frameDuration == null) {
                frameDuration = 33000000L;
            }

            if(exposureTimes != null) {
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                        Math.min(exposureTimes.getUpper(), frameDuration));
            }


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

        // first, setup thread for camera

        // quit the existing camera thread
        if(mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mBackgroundThread = new HandlerThread("CFCamera2");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        if(mFrameThread != null) {
            mFrameThread.quitSafely();
            try {
                mFrameThread.join();
                mFrameThread = null;
                mFrameHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mFrameThread = new HandlerThread("RawCamera2Frame");
        mFrameThread.start();
        mFrameHandler = new Handler(mFrameThread.getLooper());

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

            ain = Allocation.createTyped(RS, new Type.Builder(RS, Element.YUV(RS))
                    .setX(mPreviewSize.getWidth())
                    .setY(mPreviewSize.getHeight())
                    .setYuvFormat(ImageFormat.YUV_420_888)
                    .create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

            ain.setOnBufferAvailableListener(mOnBufferAvailableListener);

            RCF_BUILDER.setCamera2(manager, mCameraId, ain, mApplication.getRenderScript());

            manager.openCamera(idString, mCameraDeviceCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }


    @Override
    public String getParams() {
        String paramtxt = super.getParams();
        paramtxt += "Size: " + mPreviewSize.toString() + ", ";

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
    public void unregister() {

        super.unregister();
    }

}
