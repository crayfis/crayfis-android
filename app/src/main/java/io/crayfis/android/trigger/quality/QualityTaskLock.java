package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;

/**
 * Created by jswaney on 1/11/18.
 */

class QualityTaskLock extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {
        static final boolean DEFAULT_BACKLOCK = true;

        final boolean backLock;
        Config(String name, HashMap<String, String> options) {
            super(name, options);

            backLock = mTaskConfig.containsKey("backlock") ? Boolean.parseBoolean(mTaskConfig.get("backlock")) : DEFAULT_BACKLOCK;
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
    public boolean processFrame(RawCameraFrame frame) {
        return (frame.isFacingBack() == mConfig.backLock);
    }
}
