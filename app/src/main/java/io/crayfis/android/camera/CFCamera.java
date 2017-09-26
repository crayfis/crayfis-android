package io.crayfis.android.camera;

import android.content.Context;
import android.hardware.Camera;
import android.location.Location;
import android.os.Build;
import android.renderscript.RenderScript;

import java.util.ArrayDeque;

import io.crayfis.android.CFApplication;
import io.crayfis.android.CFConfig;
import io.crayfis.android.calibration.FrameHistory;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.precalibration.PreCalibrator;
import io.crayfis.android.ui.DataCollectionFragment;
import io.crayfis.android.util.CFLog;

import static io.crayfis.android.CFApplication.MODE_AUTO_DETECT;
import static io.crayfis.android.CFApplication.MODE_BACK_LOCK;
import static io.crayfis.android.CFApplication.MODE_FACE_DOWN;
import static io.crayfis.android.CFApplication.MODE_FRONT_LOCK;

/**
 * Created by Jeff on 8/31/2017.
 */

public abstract class CFCamera {

    CFApplication mApplication;
    final CFConfig CONFIG;
    final RenderScript RS;

    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    RawCameraFrame.Callback mCallback;

    int mCameraId = -1;

    int mResX;
    int mResY;

    final ArrayDeque<Long> mQueuedTimestamps = new ArrayDeque<>();
    final FrameHistory<Long> mTimestampHistory = new FrameHistory<>(100);



    private static CFCamera sInstance;

    CFCamera() {

        CONFIG = CFConfig.getInstance();
        RS = CFApplication.getRenderScript();
    }

    /**
     * Gets the current instance CFCamera
     *
     * @return CFCamera
     */
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

    /**
     * Instates camera, sensors, and location services
     *
     * @param context The Application context
     */
    public void register(Context context) {
        if(mCallback == null) return;
        mApplication = (CFApplication) context;
        mCFSensor = new CFSensor(context, getFrameBuilder());
        mCFLocation = new CFLocation(context, getFrameBuilder());
        changeCamera();
    }

    /**
     * Unregisters cameras, sensors, and location services
     */
    public void unregister() {
        mCameraId = -1;
        changeCamera();
        mCFSensor.unregister();
        mCFLocation.unregister();
        sInstance = null;
    }

    /**
     * Tear down camera and, if appropriate, start a new stream.
     */
    public void changeCamera() {
        changeCameraFrom(mCameraId);
    }

    /**
     * The same as changeCamera(), except marks the ID of a bad frame to avoid changing camera
     * multiple times unnecessarily
     *
     * @param currentId The cameraId of the bad frame
     */
    public synchronized void changeCameraFrom(int currentId) {
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

        mCameraId = nextId;

        if(nextId == -1 && state != CFApplication.State.IDLE ) {
            mApplication.startStabilizationTimer();
        }

        ExposureBlockManager xbManager = ExposureBlockManager.getInstance(mApplication);
        xbManager.abortExposureBlock();

        CFLog.i("cameraId:" + currentId + " -> "+ nextId);

        if(state == CFApplication.State.INIT) {
            mApplication.setApplicationState(CFApplication.State.STABILIZATION);
        }

        synchronized (mTimestampHistory) {
            mQueuedTimestamps.clear();
            mTimestampHistory.clear();
        }

    }

    /**
     * Calculates and returns the average FPS of the last 100 frames produced
     * @return double
     */
    public double getFPS() {
        long now = System.nanoTime() - CFApplication.getStartTimeNano();
        synchronized(mTimestampHistory) {
            int nframes = mTimestampHistory.size();
            if (nframes>0) {
                return ((double) nframes) / (now - mTimestampHistory.getOldest()) * 1000000000L;
            }
        }

        return 0.0;
    }

    public int getCameraId() {
        return mCameraId;
    }

    /**
     * Obtain a detailed string of camera settings to be included in the RunConfig file
     *
     * @return String
     */
    public abstract String getParams();

    /**
     * Obtain a String to be displayed in the Developer panel.
     *
     * @return String
     */
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

    public abstract RawCameraFrame.Builder getFrameBuilder();

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
        if(mCFLocation == null) return null;
        return mCFLocation.currentLocation;
    }
}
