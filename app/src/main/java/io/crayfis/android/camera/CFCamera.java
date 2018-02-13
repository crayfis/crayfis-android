package io.crayfis.android.camera;

import android.hardware.Camera;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.RenderScript;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.util.FrameHistory;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 8/31/2017.
 */

public abstract class CFCamera {

    CFApplication mApplication;
    final CFConfig CONFIG;
    RenderScript mRS;

    // thread for Camera callbacks
    protected Handler mCameraHandler;
    protected HandlerThread mCameraThread;

    // thread for posting jobs handling frames
    protected Handler mFrameHandler;
    protected HandlerThread mFrameThread;

    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    int mCameraId = -1;

    int mResX;
    int mResY;

    final FrameHistory<Long> mTimestampHistory = new FrameHistory<>(100);

    public int badFlatEvents;

    private static CFCamera sInstance;

    final RawCameraFrame.Builder RCF_BUILDER = new RawCameraFrame.Builder();

    CFCamera() {

        CONFIG = CFConfig.getInstance();
    }

    /**
     * Gets the current instance CFCamera
     *
     * @return CFCamera
     */
    public static CFCamera getInstance() {

        if(sInstance == null) {

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                sInstance = new CFCameraDeprecated();
            } else if(CFConfig.getInstance().getTargetResolution().name.equalsIgnoreCase("RAW")) {
                sInstance = new CFCamera2RAW();
            } else {
                sInstance = new CFCamera2();
            }
        }
        return sInstance;
    }

    /**
     * Instates camera, sensors, and location services
     *
     * @param app the application
     */
    public void register(CFApplication app) {
        mApplication = app;
        mRS = mApplication.getRenderScript();

        mCFSensor = new CFSensor(app, getFrameBuilder());
        mCFLocation = new CFLocation(app, getFrameBuilder());

        // start new threads if necessary
        if(mCameraHandler == null) {

            mCameraThread = new HandlerThread("CFCamera2");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());

            mFrameThread = new HandlerThread("RawCamera2Frame");
            mFrameThread.start();
            mFrameHandler = new Handler(mFrameThread.getLooper());
        }

        changeCamera();
    }

    /**
     * Unregisters cameras, sensors, and location services
     */
    public void unregister() {
        mCameraId = -1;

        changeCamera();

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

            mFrameThread.quitSafely();
            try {
                mFrameThread.join();
                mFrameThread = null;
                mFrameHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mCFSensor.unregister();
        mCFLocation.unregister();
        sInstance = null;
    }

    abstract void configure();

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
    public void changeCameraFrom(final int currentId) {

        if(currentId != mCameraId || mCameraHandler == null) return;

        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {

                // find next camera based on DAQ state
                int nextId = -1;
                CFApplication.State state = mApplication.getApplicationState();
                switch(state) {
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
                    case CALIBRATION:
                    case DATA:
                    case IDLE:
                        // take a break for a while
                        nextId = -1;
                }

                mCameraId = nextId;

                if(nextId == -1 && state != CFApplication.State.IDLE && state != CFApplication.State.FINISHED) {
                    mApplication.startStabilizationTimer();
                }

                ExposureBlockManager xbManager = ExposureBlockManager.getInstance(mApplication);
                xbManager.abortExposureBlock();

                CFLog.i("cameraId:" + currentId + " -> "+ nextId);

                configure();

                synchronized (mTimestampHistory) {
                    mTimestampHistory.clear();
                }

                if(state == CFApplication.State.INIT) {
                    mApplication.setApplicationState(CFApplication.State.STABILIZATION);
                }
            }
        });

    }

    /**
     * Calculates and returns the average FPS of the last 100 frames produced
     * @return double
     */
    public double getFPS() {
        synchronized(mTimestampHistory) {
            int nframes = mTimestampHistory.size();
            if (nframes>0) {
                return ((double) nframes) / (System.nanoTime() - mTimestampHistory.getOldest()) * 1000000000L;
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
        String devtxt = "Camera ID: " + mCameraId + ", FPS = " + String.format("%.02f", getFPS()) + "\n";
        devtxt += mCFSensor.getStatus() + mCFLocation.getStatus();
        return devtxt;
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
        return !(mCFSensor == null) && mCFSensor.isFlat();
    }

    public Location getLastKnownLocation() {
        if(mCFLocation == null) return null;
        return mCFLocation.currentLocation;
    }

    public boolean isUpdatingLocation() {
        return !(mCFLocation == null) && mCFLocation.isReceivingUpdates();
    }
}
