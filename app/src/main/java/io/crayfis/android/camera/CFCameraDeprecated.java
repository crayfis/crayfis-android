package io.crayfis.android.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.crayfis.android.R;
import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 4/16/2017.
 */

class CFCameraDeprecated extends CFCamera implements Camera.PreviewCallback, Camera.ErrorCallback {

    private Camera mCamera;
    private Camera.Parameters mParams;
    private Camera.Size previewSize;
    private SurfaceTexture mTexture;

    private static final int N_CYCLE_BUFFERS = 3;

    CFCameraDeprecated() {
        super();

        mTexture = new SurfaceTexture(10);

        mTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                RCF_BUILDER.setTimestamp(surfaceTexture.getTimestamp());
            }
        });
    }


    /**
     * Sets up the camera if it is not already setup.
     */
    @Override
    void configure() {

        // first, tear down camera
        if(mCamera != null) {

            // stop the camera preview and all processing
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            mParams = null;
            mCamera.release();
            mCamera = null;
        }

        // Open and configure the camera
        if(mCameraId == -1) return;

        try {
            mCamera = Camera.open(mCameraId);

            Camera.Parameters param= mCamera.getParameters();

            param.setExposureCompensation(0);

            // Try to pick the highest FPS range up to target FPS
            double frameRateMs = 1000 * CONFIG.getTargetFPS();
            List<int[]> validRanges = param.getSupportedPreviewFpsRange();

            String rngtxt = "Supported FPS ranges:";

            // initialize fps range to first choice
            int[] firstRange = validRanges.remove(0);
            int minfps = firstRange[0]; int maxfps = firstRange[1];

            rngtxt += "[" + minfps + "," + maxfps + "]";


            for (int[] rng : validRanges) {
                rngtxt += " [" + rng[0] + ", " + rng[1] + "]";
                if(Math.abs(rng[1] - frameRateMs) < Math.abs(maxfps - frameRateMs)) {
                    minfps = rng[0];
                    maxfps = rng[1];
                }
            }

            CFLog.i(rngtxt);
            CFLog.i("Selected FPS range: [ " + minfps + ", " + maxfps + " ]");
            try{
                // Try to set minimum=maximum FPS range.
                // This seems to work for some phones...
                param.setPreviewFpsRange(maxfps, maxfps);
            }
            catch (RuntimeException ex) {
                // but some phones will throw a fit. So just give it a "supported" range.
                CFLog.w("Unable to set maximum frame rate. Falling back to default range.");
                param.setPreviewFpsRange(minfps, maxfps);
            }
            previewSize = CONFIG.getTargetResolution().getClosestSize(mCamera);
            CFLog.i("selected preview size="+previewSize);

            param.setPreviewSize(previewSize.width, previewSize.height);

            mParams = param;
            mCamera.setParameters(mParams);

            // update the current application settings to reflect new camera size.
            mResX = previewSize.width;
            mResY = previewSize.height;

            int bufSize = previewSize.width*previewSize.height* ImageFormat.getBitsPerPixel(mParams.getPreviewFormat())/8;
            mCamera.setPreviewCallbackWithBuffer(this);
            for(int i=0; i<N_CYCLE_BUFFERS; i++) {
                mCamera.addCallbackBuffer(new byte[bufSize]);
            }

            mCamera.setPreviewTexture(mTexture);

            RCF_BUILDER.setCamera(mCamera, mRS);

            // allow other apps to access camera
            mCamera.setErrorCallback(this);

            mCamera.startPreview();

        } catch (Exception e) {
            if (e.getMessage().equals("Fail to connect to camera service")) {
                // camera is in use by another app, so just wait it out
                onError(1, mCamera);
            } else {
                e.printStackTrace();
                mApplication.userErrorMessage(R.string.camera_error,true);
            }
        }
    }

    @Override
    public void changeDataRate(boolean increase) {
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
        if(sizes == null) return;

        Collections.sort(sizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size s0, Camera.Size s1) {
                return s0.width * s0.height - s1.width * s1.height;
            }
        });

        int index = sizes.indexOf(previewSize);
        if(increase && index < sizes.size()-1) {
            index++;
        } else if(!increase && index > 0) {
            index--;
        } else {
            return;
        }

        Camera.Size newSize = sizes.get(index);
        CONFIG.setTargetResolution(newSize.width, newSize.height);

    }


    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        RCF_BUILDER.setAcquisitionTime(new AcquisitionTime())
                .setBytes(bytes);

        RawCameraFrame frame = RCF_BUILDER.build();
        mTimestampHistory.addValue(frame.getAcquiredTimeNano());
        frame.commit();

    }

    @Override
    public void onError(int errorId, Camera camera) {
        if(camera != mCamera) { return; }
        CFLog.e("Camera error " + errorId);
        changeCameraFrom(mCameraId);
    }

    @Override
    public Boolean isFacingBack() {
        if(mCameraId == -1) return null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, cameraInfo);
        return cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    @Override
    int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    @Override
    public String getParams() {
        return mParams.flatten();
    }

    @Override
    public String getStatus() {
        String devtxt = "Camera API: Deprecated \n";
        if (previewSize != null) {
            ResolutionSpec targetRes = CONFIG.getTargetResolution();

            devtxt += "Image dimensions = " + previewSize.width + "x" + previewSize.height
                    + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) + ")\n";
        }
        return devtxt + super.getStatus();
    }

}
