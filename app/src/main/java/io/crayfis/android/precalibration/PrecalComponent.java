package io.crayfis.android.precalibration;

import android.renderscript.RenderScript;

import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.RawCameraFrame;

/**
 * Created by Jeff on 7/21/2017.
 */

abstract class PrecalComponent {

    int sampleFrames;
    private AtomicInteger count;

    final RenderScript RS;
    final DataProtos.PreCalibrationResult.Builder PRECAL_BUILDER;

    PrecalComponent(RenderScript rs, DataProtos.PreCalibrationResult.Builder b) {
        RS = rs;
        PRECAL_BUILDER = b;
        count = new AtomicInteger();
    }

    boolean addFrame(RawCameraFrame frame) {
        if(count.incrementAndGet() == sampleFrames) {
            process();
            return true;
        }
        return false;
    }

    abstract void process();

}
