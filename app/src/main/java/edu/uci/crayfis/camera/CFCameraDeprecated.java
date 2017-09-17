package edu.uci.crayfis.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.util.List;

import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 4/16/2017.
 */

class CFCameraDeprecated extends CFCamera implements Camera.PreviewCallback, Camera.ErrorCallback {

    private Camera mCamera;
    private Camera.Parameters mParams;
    private Camera.Size previewSize;
    private SurfaceTexture mTexture;

    CFCameraDeprecated() {
        super();
        mTexture = new SurfaceTexture(10);

        mTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                mTimeStamps.add(surfaceTexture.getTimestamp());
            }
        });
    }


    /**
     * Sets up the camera if it is not already setup.
     */
    @Override
    public synchronized void changeCameraFrom(int currentId) {

        super.changeCameraFrom(currentId);

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
        if(mCameraId == -1) { return; }
        try {
            mCamera = Camera.open(mCameraId);
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

            param.setPreviewSize(previewSize.width, previewSize.height);

            mParams = param;
            mCamera.setParameters(mParams);

            // update the current application settings to reflect new camera size.
            mResX = previewSize.width;
            mResY = previewSize.height;

        } catch (Exception e) {
            e.printStackTrace();
            mApplication.userErrorMessage(R.string.camera_error, true);
            return;
        }

        try {
            final int N_CYCLE_BUFFERS = 5;
            int bufSize = previewSize.width*previewSize.height* ImageFormat.getBitsPerPixel(mParams.getPreviewFormat())/8;
            mCamera.setPreviewCallbackWithBuffer(this);
            for(int i=0; i<N_CYCLE_BUFFERS; i++) {
                mCamera.addCallbackBuffer(new byte[bufSize]);
            }

            mCamera.setPreviewTexture(mTexture);

            RCF_BUILDER.setCamera(mCamera, mCameraId, RS);

            // allow other apps to access camera
            mCamera.setErrorCallback(this);

            mCamera.startPreview();

        } catch (Exception e) {
            mApplication.userErrorMessage(R.string.camera_error, true);
        }
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
        changeCameraFrom(mCameraId);
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
