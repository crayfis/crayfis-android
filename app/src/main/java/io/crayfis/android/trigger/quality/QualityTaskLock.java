package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;

/**
 * Created by jswaney on 1/11/18.
 */

class QualityTaskLock extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "lock";

        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put("backlock", true);
            KEY_DEFAULT.put("maxframes", 45);
        }

        final boolean backLock;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            backLock = getBoolean("backlock");
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return QualityProcessor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new QualityTaskLock(processor, frame, this);
        }
    }

    private final Config mConfig;

    QualityTaskLock(TriggerProcessor processor, RawCameraFrame frame, Config config) {
        super(processor, frame);
        mConfig = config;
    }

    @Override
    protected int processFrame(RawCameraFrame frame) {
        return (frame.isFacingBack() == mConfig.backLock) ? 0 : 1;
    }
}
