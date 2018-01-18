package io.crayfis.android.trigger.L1;

import java.util.HashMap;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.trigger.calibration.L1Calibrator;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
class L1Task extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "default";

        static final HashMap<String, Integer> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put("thresh", 255);
            KEY_DEFAULT.put("maxframes", 1000);
        }

        final int thresh;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            thresh = mTaskConfig.get("thresh");
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L1Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor l1Processor, RawCameraFrame frame) {
            return new L1Task(l1Processor, frame, this);
        }
    }

    private ExposureBlock mExposureBlock;
    private L1Calibrator mL1Cal;
    private boolean mKeepFrame = false;
    private final Config mConfig;

    L1Task(TriggerProcessor processor, RawCameraFrame frame, Config cfg) {
        super(processor, frame);
        mExposureBlock = frame.getExposureBlock();
        mL1Cal = L1Calibrator.getInstance();
        mConfig = cfg;
    }


    @Override
    protected int processFrame(RawCameraFrame frame) {

        mL1Cal.addFrame(frame);

        if(mExposureBlock.getDAQState() == CFApplication.State.DATA) {
            L1Processor.L1CountData++;

            int max = frame.getPixMax();

            if (max > mConfig.thresh) {
                // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
                // have changed.

                // add a new buffer to the queue to make up for this one which
                // will not return
                if(frame.claim()) {
                    // this frame has passed the L1 threshold, put it on the
                    // L2 processing queue.
                    return 1;
                } else {
                    throw new OutOfMemoryError();
                }

            }
        }

        return 0;
    }
}