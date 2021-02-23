package io.crayfis.android.trigger.L1;

import java.util.HashMap;
import java.util.Random;

import io.crayfis.android.exposure.Frame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
class L1Task extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "default";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(L1Processor.KEY_L1_THRESH, 255f);
            KEY_DEFAULT.put(KEY_MAXFRAMES, 1000);
            KEY_DEFAULT.put(L1Processor.KEY_TARGET_EPM, 30f);
            KEY_DEFAULT.put(L1Processor.KEY_TRIGGER_LOCK, false);
            KEY_DEFAULT.put(L1Processor.KEY_PRESCALE, false);
        }

        final double thresh;
        final int threshBase;
        final double threshPrescale;

        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            thresh = getFloat(L1Processor.KEY_L1_THRESH);
            threshBase = (int) thresh;
            threshPrescale = thresh - threshBase;
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L1Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor l1Processor) {
            return new L1Task(l1Processor, this);
        }
    }

    private final Config mConfig;

    L1Task(TriggerProcessor processor, Config cfg) {
        super(processor);
        mConfig = cfg;
    }


    @Override
    protected int processFrame(Frame frame) {

        int max = frame.getPixMax();
        L1Processor.getCalibrator().addValue(max);

        if(frame.getExposureBlock().daq_state == CFApplication.State.DATA) {
            L1Processor.L1CountData++;

            boolean pass = (max > mConfig.threshBase + 1 ||
                    max == mConfig.threshBase + 1 && Math.random() < mConfig.threshPrescale);
            return pass ? 1 : 0;
        }

        return 0;
    }
}