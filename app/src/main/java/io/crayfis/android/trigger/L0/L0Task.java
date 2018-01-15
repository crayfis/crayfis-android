package io.crayfis.android.trigger.L0;

import org.opencv.core.Mat;

import java.util.HashMap;
import java.util.Random;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by cshimmin on 1/4/18.
 */

class L0Task extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "default";

        static final HashMap<String, Integer> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put("prescale", 1000);
            KEY_DEFAULT.put("random", 1);
            KEY_DEFAULT.put("windowsize", 10);
        }

        final int prescale;
        final boolean random;
        final int windowSize;

        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            prescale = mTaskConfig.get("prescale");
            random = mTaskConfig.get("random") != 0;
            windowSize = mTaskConfig.get("windowsize");
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L0Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new L0Task(processor, frame, this);
        }
    }

    private Config mConfig;

    L0Task(TriggerProcessor processor, RawCameraFrame frame, Config cfg) {
        super(processor, frame);
        mConfig = cfg;
    }

    boolean isZeroBias() {
        if (mConfig.random) {
            return Math.random() < 1. / mConfig.prescale;
        } else {
            return (L0Processor.L0Count.incrementAndGet() % mConfig.prescale == 0);
        }
    }

    @Override
    public boolean processFrame(RawCameraFrame frame) {

        L0Processor.L0Count.incrementAndGet();

        if (isZeroBias()) {
            CFLog.d("Yay!");
            // do the zero bias things!
            mProcessor.pass.incrementAndGet();
            Mat grayMat = frame.getGrayMat();

            // find a random pixel to be upper left corner
            Random r = new Random();
            int windowSize = mConfig.windowSize;
            int xMin = r.nextInt(grayMat.width() - windowSize + 1);
            int yMin = r.nextInt(grayMat.height() - windowSize + 1);
            Mat window = grayMat.submat(yMin, yMin + windowSize, xMin, xMin + windowSize);

            // save block to the frame
            DataProtos.ZeroBiasSquare.Builder zeroBiasBuilder = DataProtos.ZeroBiasSquare.newBuilder();
            for(int iy=0; iy<window.width(); iy++) {
                for(int ix=0; ix<window.height(); ix++) {
                    zeroBiasBuilder.addVal((int)window.get(ix, iy)[0]);
                }
            }
            frame.setZeroBias(zeroBiasBuilder.build());
            window.release();
        }

        return true;
    }
}
