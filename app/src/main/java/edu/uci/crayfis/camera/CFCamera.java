package edu.uci.crayfis.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.renderscript.RenderScript;
import android.support.v4.content.LocalBroadcastManager;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.camera.frame.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

import static edu.uci.crayfis.CFApplication.EXTRA_NEW_CAMERA;
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
    private LocalBroadcastManager mBroadcastManager;
    private final BroadcastReceiver CAMERA_CHANGE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            unSetupCamera();
            setUpAndConfigureCamera(intent.getIntExtra(EXTRA_NEW_CAMERA, 0));

            if(mApplication.getApplicationState() == CFApplication.State.INIT) {
                mApplication.setApplicationState(CFApplication.State.STABILIZATION);
            }
        }
    };
    final RenderScript RS;

    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    RawCameraFrame.Callback mCallback;

    int mResX;
    int mResY;

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

        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mBroadcastManager.registerReceiver(CAMERA_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_CAMERA_CHANGE));
    }

    public void unregister() {
        unSetupCamera();
        mBroadcastManager.unregisterReceiver(CAMERA_CHANGE_RECEIVER);
        mCFSensor.unregister();
        mCFLocation.unregister();
    }

    synchronized void setUpAndConfigureCamera(int cameraId) {

    }

    synchronized void unSetupCamera() {
    }

    public String getParams() {
        return "";
    }

    public String getStatus() {
        String devtxt = "Camera ID: " + mApplication.getCameraId() + ", Mode = ";
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
