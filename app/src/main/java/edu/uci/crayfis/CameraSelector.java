package edu.uci.crayfis;

import android.content.res.Resources;
import android.hardware.Camera;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 4/13/2017.
 */

public class CameraSelector {

    private final CFConfig CONFIG;

    public static final int MODE_FACE_DOWN = 0;
    public static final int MODE_AUTO_DETECT = 1;
    public static final int MODE_BACK_LOCK = 2;
    public static final int MODE_FRONT_LOCK = 3;

    private final CFApplication APPLICATION;

    private static CameraSelector sInstance;

    private CameraSelector(CFApplication application) {
        CONFIG = CFConfig.getInstance();
        APPLICATION = application;
    }

    public static CameraSelector getInstance(CFApplication application) {
        if(sInstance == null) {
            sInstance = new CameraSelector(application);
        }
        return sInstance;
    }

    public boolean isQuality(RawCameraFrame frame) {
        switch(CONFIG.getCameraSelectMode()) {
            case MODE_FACE_DOWN:
                float[] orientation = frame.getOrientation();
                if (orientation == null) {
                    CFLog.e("Orientation not found");
                } else if(Math.abs(frame.getOrientation()[1]) > CONFIG.getQualityOrientation()) {
                    CFLog.w("Bad event: Orientation = " + orientation[1] / Math.PI * 180 + ","
                            + orientation[2] / Math.PI * 180 + " > " + CONFIG.getQualityOrientation()/Math.PI*180);
                    return false;
                }
            case MODE_AUTO_DETECT:
                if (frame.getPixAvg() > CONFIG.getQualityBgAverage()
                        || frame.getPixStd() > CONFIG.getQualityBgVariance()) {
                    CFLog.w("Bad event: Pix avg = " + frame.getPixAvg() + ">" + CONFIG.getQualityBgAverage());
                    return false;
                } else {
                    return true;
                }
            case MODE_BACK_LOCK:
                return frame.getCameraId() == 0;
            case MODE_FRONT_LOCK:
                return frame.getCameraId() == 1;
            default:
                throw new RuntimeException("Invalid camera select mode");
        }
    }

    public void changeCamera() {
        int nextId = -1;
        switch(APPLICATION.getApplicationState()) {
            case RECONFIGURE:
                nextId = APPLICATION.getCameraId();
            case STABILIZATION:
                // switch cameras and try again
                switch(CONFIG.getCameraSelectMode()) {
                    case MODE_FACE_DOWN:
                    case MODE_AUTO_DETECT:
                        nextId = APPLICATION.getCameraId() + 1;
                        if(nextId >= Camera.getNumberOfCameras()) {
                            nextId = -1;
                        }
                        break;
                    case MODE_BACK_LOCK:
                        nextId = 0;
                        break;
                    case MODE_FRONT_LOCK:
                        nextId = 1;
                }
                break;
            case CALIBRATION:
            case DATA:
            case IDLE:
                // take a break for a while
                nextId = -1;
        }
        APPLICATION.setCameraId(nextId);
    }
}
