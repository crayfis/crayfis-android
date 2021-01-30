package io.crayfis.android.daq;

import android.location.Location;

import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.main.CFApplication;

/**
 * A wrapper class for coordinating and accessing cameras, sensors, and GPS
 *
 * Created by Jeff on 8/31/2017.
 */

public class DAQManager {

    private CFApplication mApplication;

    private CFCamera mCFCamera;
    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    private static DAQManager sInstance;

    final RawCameraFrame.Builder RCF_BUILDER = new RawCameraFrame.Builder();

    DAQManager() { }

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
     * Instates camera, sensors, and location services
     *
     * @param app the application
     */
    public synchronized void register(CFApplication app) {

        if(mApplication != null) return; // already registered

        mApplication = app;

        mCFCamera = new CFCamera(app, RCF_BUILDER);
        mCFSensor = new CFSensor(app, RCF_BUILDER);
        mCFLocation = new CFLocation(app, RCF_BUILDER);

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
        RCF_BUILDER.setExposureBlock(xb);
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
        return mCFLocation.currentLocation;
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
