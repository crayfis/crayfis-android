package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by jswaney on 1/12/18.
 */

class QualityTaskAutodetect extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "autodetect";

        static final HashMap<String, Integer> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put("avg", 10);
            KEY_DEFAULT.put("std", 255);
            KEY_DEFAULT.put("maxframes", 45);
        }

        final int avgCut;
        final int stdCut;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            avgCut = mTaskConfig.get("avg");
            stdCut = mTaskConfig.get("std");
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return QualityProcessor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new QualityTaskAutodetect(processor, frame, this);
        }
    }

    private final Config mConfig;

    QualityTaskAutodetect(TriggerProcessor processor, RawCameraFrame frame, Config config) {
        super(processor, frame);
        mConfig = config;
    }

    @Override
    public int processFrame(RawCameraFrame frame) {
        if (frame.getPixAvg() > mConfig.avgCut
                || frame.getPixStd() > mConfig.stdCut) {
            CFLog.w("Bad event: Pix avg = " + frame.getPixAvg() + ">" + mConfig.avgCut);
            return 0;
        } else {
            return 1;
        }
    }

}
