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

        static final int DEFAULT_PRESCALE = 1000;
        static final boolean DEFAULT_RANDOM = true;
        static final int DEFAULT_WINDOWSIZE = 10;

        final int prescale;
        final boolean random;
        final int windowSize;

        Config(String name, HashMap<String, String> options) {
            super(name, options);

            prescale = CFUtil.getInt(mTaskConfig.get("prescale"), DEFAULT_PRESCALE);
            random = mTaskConfig.containsKey("random") ? Boolean.parseBoolean(mTaskConfig.get("random")) : DEFAULT_RANDOM;
            windowSize = CFUtil.getInt(mTaskConfig.get("windowsize"), DEFAULT_WINDOWSIZE);
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
            return (L0Processor.L0Count % mConfig.prescale == 0);
        }
    }

    @Override
    public boolean processFrame(RawCameraFrame frame) {

        L0Processor.L0Count++;

        if (isZeroBias()) {
            // do the zero bias things!
            mProcessor.pass++;
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
