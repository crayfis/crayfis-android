package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by jswaney on 1/12/18.
 */

public class QualityTaskAutodetect extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {
        static final int DEFAULT_AVG_CUT = 10;
        static final int DEFAULT_STD_CUT = 255;

        final int avgCut;
        final int stdCut;
        Config(String name, HashMap<String, String> options) {
            super(name, options);

            avgCut = CFUtil.getInt(options.get("avg"), DEFAULT_AVG_CUT);
            stdCut = CFUtil.getInt(options.get("std"), DEFAULT_STD_CUT);
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
    public boolean processFrame(RawCameraFrame frame) {
        if (frame.getPixAvg() > mConfig.avgCut
                || frame.getPixStd() > mConfig.stdCut) {
            CFLog.w("Bad event: Pix avg = " + frame.getPixAvg() + ">" + mConfig.avgCut);
            return false;
        } else {
            return true;
        }
    }

}
