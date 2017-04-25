package edu.uci.crayfis.calibration;

import edu.uci.crayfis.camera.RawCameraFrame;

/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private PreCalibrator() {}

    private static PreCalibrator sInstance;

    public static PreCalibrator getInstance() {
        if(sInstance == null) {
            sInstance = new PreCalibrator();
        }
        return sInstance;
    }

    public void addFrame(RawCameraFrame frame) {

    }
}
