package edu.uci.crayfis.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.util.List;

import edu.uci.crayfis.R;
import edu.uci.crayfis.camera.frame.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 4/16/2017.
 */

class CFCameraDeprecated extends CFCamera implements Camera.PreviewCallback, Camera.ErrorCallback {

    private Camera mCamera;
    private Camera.Parameters mParams;
    private Camera.Size previewSize;
    private SurfaceTexture mTexture;
    private final int N_CYCLE_BUFFERS = 5;

    CFCameraDeprecated() {
        super();
    }


    /**
     * Sets up the camera if it is not already setup.
     */
    @Override
    synchronized void setUpAndConfigureCamera(int cameraId) {
        // Open and configure the camera
        if(mCamera != null && previewSize != null || cameraId == -1) { return; }
        try {
            mCamera = Camera.open(cameraId);
        } catch (Exception e) {
            if (e.getMessage().equals("Fail to connect to camera service")) {
                // camera is in use by another app, so just wait it out
                onError(1, mCamera);
                return;
            } else {
                mApplication.userErrorMessage(R.string.camera_error,true);
                return;
            }
        }

        try {

            Camera.Parameters param= mCamera.getParameters();

            // param.setFocusMode("FIXED");
            param.setExposureCompensation(0);

            // Try to pick the highest FPS range possible
            int minfps = 0, maxfps = 0;
            List<int[]> validRanges = param.getSupportedPreviewFpsRange();
            if (validRanges != null)
                for (int[] rng : validRanges) {
                    CFLog.i("Supported FPS range: [ " + rng[0] + ", " + rng[1] + " ]");
                    if (rng[1] > maxfps || (rng[1] == maxfps && rng[0] > minfps)) {
                        maxfps = rng[1];
                        minfps = rng[0];
                    }
                }
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

            CFLog.d("setup: size is width=" + previewSize.width + " height =" + previewSize.height);
            param.setPreviewSize(previewSize.width, previewSize.height);

            mParams = param;
            mCamera.setParameters(mParams);
            CFLog.d("params: Camera params are " + param.flatten());

            // update the current application settings to reflect new camera size.
            mResX = previewSize.width;
            mResY = previewSize.height;

        } catch (Exception e) {
            e.printStackTrace();
            mApplication.userErrorMessage(R.string.camera_error, true);
            return;
        }

        try {
            int bufSize = previewSize.width*previewSize.height* ImageFormat.getBitsPerPixel(mParams.getPreviewFormat())/8;
            if(N_CYCLE_BUFFERS > 0) {
                mCamera.setPreviewCallbackWithBuffer(this);
                for(int i=0; i<N_CYCLE_BUFFERS; i++) {
                    mCamera.addCallbackBuffer(new byte[bufSize]);
                }
            } else {
                mCamera.setPreviewCallback(this);
            }
            mTexture = new SurfaceTexture(10);
            mCamera.setPreviewTexture(mTexture);

            RCF_BUILDER.setCamera(mCamera, cameraId, RS);

            // allow other apps to access camera
            mCamera.setErrorCallback(this);

            mCamera.startPreview();

            super.setUpAndConfigureCamera(cameraId);

        } catch (Exception e) {
            mApplication.userErrorMessage(R.string.camera_error, true);
        }
    }

    @Override
    synchronized void unSetupCamera() {

        super.unSetupCamera();

        if(mCamera == null) return;

        // stop the camera preview and all processing
        mCamera.stopPreview();
        if (N_CYCLE_BUFFERS>0) {
            mCamera.setPreviewCallbackWithBuffer(null);
        } else {
            mCamera.setPreviewCallback(null);
        }
        mTexture = null;
        mParams = null;
        mCamera.release();
        mCamera = null;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

        if(mCallback != null) {
            mCallback.onRawCameraFrame(RCF_BUILDER.setBytes(bytes)
                    .setAcquisitionTime(new AcquisitionTime())
                    .setTimestamp(mTexture.getTimestamp())
                    .build());
        }

    }

    @Override
    public void onError(int errorId, Camera camera) {
        if(camera != mCamera) { return; }
        CFLog.e("Camera error " + errorId);
        mApplication.changeCamera();
    }

    @Override
    public String getParams() {
        String paramtxt = super.getStatus();
        paramtxt += mParams.flatten();
        return paramtxt;
    }

    @Override
    public String getStatus() {
        String devtxt = "Camera API: Deprecated \n" + super.getStatus();
        if (previewSize != null) {
            ResolutionSpec targetRes = CONFIG.getTargetResolution();

            devtxt += "Image dimensions = " + previewSize.width + "x" + previewSize.height
                    + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) + ")\n";
        }
        return devtxt;
    }

}
