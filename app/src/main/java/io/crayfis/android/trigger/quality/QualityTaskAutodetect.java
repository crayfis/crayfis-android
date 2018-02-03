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
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(QualityProcessor.KEY_MEAN_THRESH, 10f);
            KEY_DEFAULT.put(QualityProcessor.KEY_ST_DEV_THRESH, 255f);
            KEY_DEFAULT.put(KEY_MAXFRAMES, 45);
        }

        final double avgCut;
        final double stdCut;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            avgCut = getFloat(QualityProcessor.KEY_MEAN_THRESH);
            stdCut = getFloat(QualityProcessor.KEY_ST_DEV_THRESH);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return QualityProcessor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new QualityTaskAutodetect(processor, this);
        }
    }

    private final Config mConfig;

    QualityTaskAutodetect(TriggerProcessor processor, Config config) {
        super(processor);
        mConfig = config;
    }

    @Override
    protected int processFrame(RawCameraFrame frame) {
        if (frame.getPixAvg() > mConfig.avgCut
                || frame.getPixStd() > mConfig.stdCut) {
            CFLog.w("Bad event: Pix avg = " + frame.getPixAvg() + ">" + mConfig.avgCut);
            return 0;
        } else {
            return 1;
        }
    }

}
