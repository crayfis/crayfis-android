package edu.uci.crayfis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.renderscript.RenderScript;
import android.support.v4.content.LocalBroadcastManager;

import java.util.List;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.camera.ResolutionSpec;
import edu.uci.crayfis.util.CFLog;

import static edu.uci.crayfis.CFApplication.EXTRA_NEW_CAMERA;
import static edu.uci.crayfis.CFApplication.MODE_AUTO_DETECT;
import static edu.uci.crayfis.CFApplication.MODE_BACK_LOCK;
import static edu.uci.crayfis.CFApplication.MODE_FACE_DOWN;
import static edu.uci.crayfis.CFApplication.MODE_FRONT_LOCK;

/**
 * Created by Jeff on 4/16/2017.
 */

public class CFCamera implements Camera.ErrorCallback {

    private Camera mCamera;
    private Camera.Parameters mParams;
    private Camera.Size previewSize;
    private SurfaceTexture mTexture;
    private RenderScript mRS;
    private Camera.PreviewCallback mPreviewCallback;
    private LocalBroadcastManager mBroadcastManager;
    private final int N_CYCLE_BUFFERS = 7;

    private final RawCameraFrame.Builder BUILDER;
    private final CFApplication APPLICATION;
    private final CFConfig CONFIG;
    private final BroadcastReceiver CAMERA_CHANGE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            unSetupCamera();
            setUpAndConfigureCamera(intent.getIntExtra(EXTRA_NEW_CAMERA, 0));
        }
    };

    private static CFCamera sInstance;

    public static CFCamera getInstance(Context context, RawCameraFrame.Builder frameBuilder,
                                       Camera.PreviewCallback previewCallback) {
        if(sInstance == null) {
            sInstance = new CFCamera(context, frameBuilder, previewCallback);
        }
        return sInstance;
    }

    private CFCamera(Context context, RawCameraFrame.Builder frameBuilder,
                     Camera.PreviewCallback previewCallback) {
        BUILDER = frameBuilder;
        APPLICATION = (CFApplication) context.getApplicationContext();
        CONFIG = CFConfig.getInstance();
        mRS = RenderScript.create(context);
        mPreviewCallback = previewCallback;

        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mBroadcastManager.registerReceiver(CAMERA_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_CAMERA_CHANGE));
    }

    public void unregister() {
        unSetupCamera();
        mBroadcastManager.unregisterReceiver(CAMERA_CHANGE_RECEIVER);
        sInstance = null;
    }

    /**
     * Sets up the camera if it is not already setup.
     */
    private synchronized void setUpAndConfigureCamera(int cameraId) {
        // Open and configure the camera
        CFLog.d("setUpAndConfigureCamera()");
        if(mCamera != null && CFApplication.getCameraSize() != null
                || cameraId == -1) { return; }
        try {
            mCamera = Camera.open(cameraId);
        } catch (Exception e) {
            if (e.getMessage().equals("Fail to connect to camera service")) {
                // camera is in use by another app, so just wait it out
                onError(1, mCamera);
                return;
            } else {
                // TODO: fix user error message
                //userErrorMessage(getResources().getString(R.string.camera_error), true);
                return;
            }
        }
        CFLog.d("Camera opened camera="+mCamera);
        try {

            Camera.Parameters param= mCamera.getParameters();

            previewSize = CONFIG.getTargetResolution().getClosestSize(mCamera);
            CFLog.i("selected preview size="+previewSize);

            CFLog.d("setup: size is width=" + previewSize.width + " height =" + previewSize.height);
            param.setPreviewSize(previewSize.width, previewSize.height);
            // param.setFocusMode("FIXED");
            param.setExposureCompensation(0);

            // Try to pick the highest FPS range possible
            int minfps = 0, maxfps = 0;
            List<int[]> validRanges = param.getSupportedPreviewFpsRange();
            if (validRanges != null)
                for (int[] rng : validRanges) {
                    CFLog.i("DAQActivity Supported FPS range: [ " + rng[0] + ", " + rng[1] + " ]");
                    if (rng[1] > maxfps || (rng[1] == maxfps && rng[0] > minfps)) {
                        maxfps = rng[1];
                        minfps = rng[0];
                    }
                }
            CFLog.i("DAQActivity Selected FPS range: [ " + minfps + ", " + maxfps + " ]");
            try{
                // Try to set minimum=maximum FPS range.
                // This seems to work for some phones...
                param.setPreviewFpsRange(maxfps, maxfps);
            }
            catch (RuntimeException ex) {
                // but some phones will throw a fit. So just give it a "supported" range.
                CFLog.w("DAQActivity Unable to set maximum frame rate. Falling back to default range.");
                param.setPreviewFpsRange(minfps, maxfps);
            }
            mParams = param;
            mCamera.setParameters(mParams);
            CFLog.d("params: Camera params are " + param.flatten());

            // update the current application settings to reflect new camera size.
            CFApplication.setCameraParams(mParams);

        } catch (Exception e) {
            e.printStackTrace();
            // userErrorMessage(getResources().getString(R.string.camera_error),true);
            return;
        }

        try {
            int bufSize = previewSize.width*previewSize.height* ImageFormat.getBitsPerPixel(mParams.getPreviewFormat())/8;
            if(N_CYCLE_BUFFERS > 0) {
                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                for(int i=0; i<N_CYCLE_BUFFERS; i++) {
                    mCamera.addCallbackBuffer(new byte[bufSize]);
                }
            } else {
                mCamera.setPreviewCallback(mPreviewCallback);
            }
            mTexture = new SurfaceTexture(10);
            mCamera.setPreviewTexture(mTexture);

            // configure RenderScript if available
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                CFLog.i("Configuring RenderScript");

                BUILDER.setCamera(mCamera, mRS)
                        .setCameraId(cameraId);
            } else {
                BUILDER.setCamera(mCamera)
                        .setCameraId(cameraId);
            }

            // allow other apps to access camera
            mCamera.setErrorCallback(this);

            mCamera.startPreview();
        }  catch (Exception e) {
            // userErrorMessage(getResources().getString(R.string.camera_error),true);
        }
    }

    private synchronized void unSetupCamera() {

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

        // clear out any (old) buffers
        //l2thread.clearQueue();
        // FIXME: should we abort any AsyncTasks in the L1/L2 queue that haven't executed yet?

        CFLog.d(" DAQActivity: unsetup camera");
    }

    @Override
    public void onError(int errorId, Camera camera) {
        if(camera != mCamera) { return; }
        CFLog.e("Camera error " + errorId);
        APPLICATION.changeCamera();
    }

    public String getStatus() {
        if (previewSize != null) {
            String devtxt = "";
            ResolutionSpec targetRes = CONFIG.getTargetResolution();
            devtxt += "Camera ID: " + APPLICATION.getCameraId() + ", Mode = ";
            switch (CONFIG.getCameraSelectMode()) {
                case MODE_FACE_DOWN:
                    devtxt += "FACE-DOWN\n";
                    break;
                case MODE_AUTO_DETECT:
                    devtxt += "AUTO-DETECT\n";
                    break;
                case MODE_BACK_LOCK:
                    devtxt += "BACK LOCK\n";
                    break;
                case MODE_FRONT_LOCK:
                    devtxt += "FRONT LOCK\n";
                    break;

            }
            devtxt += "Image dimensions = " + previewSize.width + "x" + previewSize.height
                    + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) + ")\n";
            return devtxt;
        }
        return null;
    }

}
