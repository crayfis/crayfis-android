package io.crayfis.android.trigger;

import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

public class L0Task implements Runnable {
    public static class Config extends L0Config {
        Config(String name, String cfg) {
            super(name, cfg);
        }

        @Override
        public L0Task makeTask(L0Processor l0Processor, RawCameraFrame frame) {
            return new L0Task(l0Processor, frame);
        }
    }

    private L0Processor mL0Processor = null;
    private RawCameraFrame mFrame = null;
    private ExposureBlock mExposureBlock = null;
    private CFApplication mApplication = null;

    public L0Task(L0Processor l0Processor, RawCameraFrame frame) {
        mL0Processor = l0Processor;
        mFrame = frame;
        mExposureBlock = mFrame.getExposureBlock();

        mApplication = mL0Processor.mApplication;
    }

    public boolean isZeroBias() {
        return false;
    }

    @Override
    public void run() {
        if (isZeroBias()) {
            // do the zero bias things!
        }

        // now hand off to the L1 processor.
        mL0Processor.mL1Processor.submitFrame(mFrame);
    }
}
