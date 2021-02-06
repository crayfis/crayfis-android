package io.crayfis.android.daq;

import android.location.Location;

import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.main.CFApplication;

/**
 * A wrapper class for coordinating and accessing cameras, sensors, and GPS
 *
 * Created by Jeff on 8/31/2017.
 */

public class DAQManager {

    private CFApplication mApplication;

    private final CFCamera mCFCamera;
    private final CFSensor mCFSensor;
    private final CFLocation mCFLocation;

    private static DAQManager sInstance;

    final Frame.Builder FRAME_BUILDER = new Frame.Builder();

    DAQManager() {
        // initialize these before starting DAQ to avoid NullPointers
        // from getter methods

        // this also avoids the possibility of multiple Frame.Builders

        mCFCamera = new CFCamera(FRAME_BUILDER);
        mCFSensor = new CFSensor(FRAME_BUILDER);
        mCFLocation = new CFLocation(FRAME_BUILDER);
    }

    /**
     * Gets the current instance
     *
     * @return DAQManager
     */
    public static synchronized DAQManager getInstance() {
        if(sInstance == null) {
            sInstance = new DAQManager();
        }
        return sInstance;
    }

    /**
     * Instantiates camera, sensors, and location services
     *
     * @param app the application
     */
    public synchronized void register(CFApplication app) {

        if(mApplication != null) return; // already registered

        mApplication = app;

        mCFCamera.register(app);
        mCFSensor.register(app);
        mCFLocation.register(app);

        changeCamera();
    }

    /**
     * Unregisters cameras, sensors, and location services
     */
    public synchronized void unregister() {

        mCFCamera.unregister();
        mCFSensor.unregister();
        mCFLocation.unregister();
        sInstance = null;
    }

    /**
     * Tear down camera and, if appropriate, start a new stream.
     */
    public void changeCamera() {
        changeCameraFrom(getCameraId());
    }

    public void changeCameraFrom(final int currentId) {
        changeCameraFrom(currentId, false);
    }

    /**
     * The same as changeCamera(), except marks the ID of a bad frame to avoid changing camera
     * multiple times unnecessarily
     *
     * @param currentId The cameraId of the bad frame
     * @param quit Whether we quit the thread after changing the camera
     */
    public void changeCameraFrom(final int currentId, final boolean quit) {
        mCFCamera.changeCameraFrom(currentId, quit);
    }

    public void changeDataRate(boolean increase) {
        mCFCamera.changeDataRate(increase);
    }

    public void setExposureBlock(ExposureBlock xb) {
        FRAME_BUILDER.setExposureBlock(xb);
    }

    public boolean isStreamingRAW() {
        return mCFCamera.isStreamingRAW();
    }

    public int getCameraId() {
        return mCFCamera.getCameraId();
    }

    public int getResX() {
        return mCFCamera.getResX();
    }

    public int getResY() {
        return mCFCamera.getResY();
    }

    public double getFPS() {
        return mCFCamera.getFPS();
    }

    public Boolean isCameraFacingBack() {
        return mCFCamera.isFacingBack();
    }

    public boolean isPhoneFlat() {
        return mCFSensor == null || mCFSensor.isFlat();
    }

    public Location getLastKnownLocation() {
        if(mCFLocation == null) return null;
        return mCFLocation.getLastKnownLocation();
    }

    public boolean isUpdatingLocation() {
        return !(mCFLocation == null) && mCFLocation.isReceivingUpdates();
    }

    /**
     * Obtain a String to be displayed in the Developer panel.
     *
     * @return String
     */
    public String getStatus() {
        return mCFCamera.getStatus() + mCFSensor.getStatus() + mCFLocation.getStatus();
    }

    /**
     * Obtain a detailed string of camera settings to be included in the RunConfig file
     *
     * @return String
     */
    public String getCameraParams() {
        return mCFCamera.getParams();
    }
}
