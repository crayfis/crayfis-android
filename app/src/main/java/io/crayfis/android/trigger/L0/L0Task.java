package io.crayfis.android.trigger.L0;

import org.opencv.core.Mat;

import java.util.HashMap;
import java.util.Random;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

class L0Task extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "default";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(L0Processor.KEY_PRESCALE, .001f);
            KEY_DEFAULT.put(L0Processor.KEY_RANDOM, true);
            KEY_DEFAULT.put(L0Processor.KEY_WINDOWSIZE, 10);
        }

        final double prescale;
        final boolean random;
        final int windowSize;

        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            prescale = getFloat(L0Processor.KEY_PRESCALE);
            random = getBoolean(L0Processor.KEY_RANDOM);
            windowSize = getInt(L0Processor.KEY_WINDOWSIZE);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L0Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new L0Task(processor, this);
        }
    }

    private Config mConfig;

    L0Task(TriggerProcessor processor, Config cfg) {
        super(processor);
        mConfig = cfg;
    }

    boolean isZeroBias() {
        if (mConfig.random) {
            return Math.random() < mConfig.prescale;
        } else {
            return (L0Processor.L0Count.incrementAndGet() % (int)(1/mConfig.prescale) == 0);
        }
    }

    @Override
    protected int processFrame(RawCameraFrame frame) {

        L0Processor.L0Count.incrementAndGet();

        if (isZeroBias()) {
            // do the zero bias things!
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

        return 1;
    }
}
