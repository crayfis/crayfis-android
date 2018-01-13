package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by jswaney on 1/11/18.
 */

class QualityTaskFacedown extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "facedown";

        static final HashMap<String, Integer> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put("orientation", 10);
            KEY_DEFAULT.put("avg", 10);
            KEY_DEFAULT.put("std", 255);
        }

        final double orientationCosine;
        final int avgCut;
        final int stdCut;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            orientationCosine = Math.cos(Math.PI/180. * mTaskConfig.get("orientation"));
            avgCut = mTaskConfig.get("avg");
            stdCut = mTaskConfig.get("std");
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return QualityProcessor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new QualityTaskFacedown(processor, frame, this);
        }
    }

    private final Config mConfig;

    QualityTaskFacedown(TriggerProcessor processor, RawCameraFrame frame, Config config) {
        super(processor, frame);
        mConfig = config;
    }

    @Override
    public boolean processFrame(RawCameraFrame frame) {
        if (frame.getOrientation() == null) {
            CFLog.e("Orientation not found");
        } else {

            // use quaternion algebra to calculate cosine of angle between vertical
            // and phone's z axis (up to a sign that tends to have numerical instabilities)

            if(Math.abs(frame.getRotationZZ()) < mConfig.orientationCosine
                    || frame.isFacingBack() != frame.getRotationZZ()>0) {

                CFLog.w("Bad event: Orientation = " + frame.getRotationZZ());
                return false;
            }
        }
        if (frame.getPixAvg() > mConfig.avgCut
                || frame.getPixStd() > mConfig.stdCut) {
            CFLog.w("Bad event: Pix avg = " + frame.getPixAvg() + ">" + mConfig.avgCut);
            return false;
        } else {
            return true;
        }
    }
}
