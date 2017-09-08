package edu.uci.crayfis.precalibration;

import android.renderscript.RenderScript;

import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.camera.frame.RawCameraFrame;

/**
 * Created by Jeff on 7/21/2017.
 */

abstract class PrecalComponent {

    int sampleFrames;
    private int count;

    final RenderScript RS;
    final DataProtos.PreCalibrationResult.Builder RCF_BUILDER;

    PrecalComponent(RenderScript rs, DataProtos.PreCalibrationResult.Builder b) {
        RS = rs;
        RCF_BUILDER = b;
        count = 0;
    }

    boolean addFrame(RawCameraFrame frame) {
        if(++count == sampleFrames) {
            process();
            return true;
        }
        return false;
    }

    void process() { }

}
