package edu.uci.crayfis.camera;

import android.content.Context;
import android.hardware.Camera;
import android.location.Location;
import android.os.Build;
import android.renderscript.RenderScript;

import java.util.ArrayDeque;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.camera.frame.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlockManager;
import edu.uci.crayfis.precalibration.PreCalibrator;
import edu.uci.crayfis.ui.DataCollectionFragment;
import edu.uci.crayfis.util.CFLog;

import static edu.uci.crayfis.CFApplication.MODE_AUTO_DETECT;
import static edu.uci.crayfis.CFApplication.MODE_BACK_LOCK;
import static edu.uci.crayfis.CFApplication.MODE_FACE_DOWN;
import static edu.uci.crayfis.CFApplication.MODE_FRONT_LOCK;

/**
 * Created by Jeff on 8/31/2017.
 */

public abstract class CFCamera {

    final RawCameraFrame.Builder RCF_BUILDER = new RawCameraFrame.Builder();

    CFApplication mApplication;
    final CFConfig CONFIG;
    final RenderScript RS;

    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    RawCameraFrame.Callback mCallback;

    int mCameraId = -1;

    int mResX;
    int mResY;

    ArrayDeque<Long> mTimeStamps = new ArrayDeque<>();


    private static CFCamera sInstance;

    CFCamera() {

        CONFIG = CFConfig.getInstance();
        RS = CFApplication.getRenderScript();

    }

    public static CFCamera getInstance() {

        if(sInstance == null) {

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                sInstance = new CFCamera2();
            } else {
                sInstance = new CFCameraDeprecated();
            }
        }
        return sInstance;
    }
    
    public void register(Context context) {
        mApplication = (CFApplication) context;
        mCFSensor = new CFSensor(context, RCF_BUILDER);
        mCFLocation = new CFLocation(context, RCF_BUILDER);
        changeCamera();
    }

    public void unregister() {
        mCameraId = -1;
        changeCamera();
        mCFSensor.unregister();
        mCFLocation.unregister();
        sInstance = null;
    }

    public void changeCamera() {
        changeCameraFrom(mCameraId);
    }

    public void changeCameraFrom(int currentId) {
        if(currentId != mCameraId) {
            return;
        }

        int nextId = -1;
        CFApplication.State state = mApplication.getApplicationState();
        switch(state) {
            case RECONFIGURE:
                nextId = currentId;
                break;
            case INIT:
                nextId = 0;
                break;
            case STABILIZATION:
                // switch cameras and try again
                nextId = currentId + 1;
                if(nextId >= Camera.getNumberOfCameras()) {
                    nextId = -1;
                }
                break;
            case PRECALIBRATION:
                PreCalibrator.getInstance(mApplication).clear();
            case CALIBRATION:
            case DATA:
            case IDLE:
                // take a break for a while
                nextId = -1;
        }

        if(mCameraId == nextId && state != CFApplication.State.RECONFIGURE) {
            return;
        }

        mCameraId = nextId;

        if(nextId == -1 && state != CFApplication.State.IDLE ) {

            DataCollectionFragment.getInstance().updateIdleStatus("No available cameras: waiting to retry");
            mApplication.startStabilizationTimer();
        }

        ExposureBlockManager xbManager = ExposureBlockManager.getInstance(mApplication);
        xbManager.abortExposureBlock();

        CFLog.i("cameraId:" + currentId + " -> "+ nextId);

        if(state == CFApplication.State.INIT) {
            mApplication.setApplicationState(CFApplication.State.STABILIZATION);
        }

        mTimeStamps.clear();

    }

    public int getCameraId() {
        return mCameraId;
    }

    public String getParams() {
        return "";
    }

    public String getStatus() {
        String devtxt = "Camera ID: " + mCameraId + ", Mode = ";
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
        devtxt += mCFSensor.getStatus() + mCFLocation.getStatus();
        return devtxt;
    }
    
    public void setCallback(RawCameraFrame.Callback callback) {
        mCallback = callback;
    }

    public RawCameraFrame.Builder getFrameBuilder() {
        return RCF_BUILDER;
    }

    public int getResX() {
        return mResX;
    }

    public int getResY() {
        return mResY;
    }

    public boolean isFlat() {
        return mCFSensor.isFlat();
    }

    public Location getLastKnownLocation() {
        return mCFLocation.currentLocation;
    }
}
