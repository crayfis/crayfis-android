package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 1/11/18.
 */

class QualityTaskFacedown extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "facedown";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(QualityProcessor.KEY_ORIENT_THRESH, 10f);
            KEY_DEFAULT.put(QualityProcessor.KEY_MEAN_THRESH, 10f);
            KEY_DEFAULT.put(QualityProcessor.KEY_ST_DEV_THRESH, 255f);
            KEY_DEFAULT.put(KEY_MAXFRAMES, 45);
        }

        final double orientationCosine;
        final double avgCut;
        final double stdCut;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            orientationCosine = Math.cos(Math.PI/180. * getFloat(QualityProcessor.KEY_ORIENT_THRESH));
            avgCut = getFloat(QualityProcessor.KEY_MEAN_THRESH);
            stdCut = getFloat(QualityProcessor.KEY_ST_DEV_THRESH);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return QualityProcessor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new QualityTaskFacedown(processor, this);
        }
    }

    private final Config mConfig;

    QualityTaskFacedown(TriggerProcessor processor, Config config) {
        super(processor);
        mConfig = config;
    }

    @Override
    protected int processFrame(RawCameraFrame frame) {
        if (frame.getOrientation() == null) {
            CFLog.e("Orientation not found");
        } else {

            // use quaternion algebra to calculate cosine of angle between vertical
            // and phone's z axis (up to a sign that tends to have numerical instabilities)

            if(Math.abs(frame.getRotationZZ()) < mConfig.orientationCosine
                    || frame.isFacingBack() != frame.getRotationZZ()>0) {

                CFLog.w("Bad event: Orientation = " + frame.getRotationZZ());
                return 0;
            }
        }
        if (frame.getPixAvg() > mConfig.avgCut
                || frame.getPixStd() > mConfig.stdCut) {
            CFLog.w("Bad event: Pix avg = " + frame.getPixAvg() + ">" + mConfig.avgCut);
            return 0;
        } else {
            return 1;
        }
    }
}
