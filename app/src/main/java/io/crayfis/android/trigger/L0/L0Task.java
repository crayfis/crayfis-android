package io.crayfis.android.trigger.L0;

import org.opencv.core.Mat;

import java.util.Random;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

class L0Task implements Runnable {
    public static class Config extends L0Config {

        public final int DEFAULT_PRESCALE = 1000;
        public final boolean DEFAULT_RANDOM = true;
        public final int DEFAULT_WINDOWSIZE = 10;

        public final int prescale;
        public final boolean random;
        public final int windowSize;

        Config(String name, String cfg) {
            super(name, cfg);

            // FIXME: there's probably an easier/more generic way to parse simple key-val pairs.
            int cfg_prescale = DEFAULT_PRESCALE;
            boolean cfg_random = DEFAULT_RANDOM;
            int cfg_windowSize = DEFAULT_WINDOWSIZE;
            for (String c : cfg.split(";")) {
                String[] kv = c.split("=");
                CFLog.i("parsing c='" + c + "', split len = " + kv.length);
                if (kv.length != 2) continue;
                String key = kv[0];
                String value = kv[1];
                CFLog.i("key='" + key +"', val='" + value +"'");

                switch (key) {
                    case "prescale":
                        try {
                            cfg_prescale = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            CFLog.w("Couldn't parse prescale argument for L0 configuraion!");
                        }
                        break;
                    case "windowsize":
                        try {
                            cfg_windowSize = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            CFLog.w("Couldn't parse windowsize argument for L0 configuraion!");
                        }
                        break;
                    case "random":
                        cfg_random = Boolean.parseBoolean(value);
                        break;

                }
            }

            prescale = cfg_prescale;
            random = cfg_random;
            windowSize = cfg_windowSize;
        }

        @Override
        public L0Task makeTask(L0Processor l0Processor, RawCameraFrame frame) {
            return new L0Task(l0Processor, frame, this);
        }
    }

    private L0Processor mL0Processor;
    private RawCameraFrame mFrame;
    private ExposureBlock mExposureBlock;
    private CFApplication mApplication;
    private Config mConfig;

    L0Task(L0Processor l0Processor, RawCameraFrame frame, Config cfg) {
        mL0Processor = l0Processor;
        mFrame = frame;
        mExposureBlock = mFrame.getExposureBlock();
        mConfig = cfg;

        mApplication = mL0Processor.mApplication;
    }

    boolean isZeroBias() {
        if (mConfig.random) {
            return Math.random() < 1. / mConfig.prescale;
        } else {
            return (++L0Processor.mL0Count % mConfig.prescale == 0);
        }
    }

    @Override
    public void run() {

        if (isZeroBias()) {
            // do the zero bias things!
            Mat grayMat = mFrame.getGrayMat();

            // find a random pixel to be upper left corner
            Random r = new Random();
            int windowSize = mConfig.windowSize;
            int xMin = r.nextInt(grayMat.width() - windowSize + 1);
            int yMin = r.nextInt(grayMat.height() - windowSize + 1);
            Mat window = grayMat.submat(xMin, xMin + windowSize, yMin, yMin + windowSize);

            // save block to the frame
            mFrame.setZeroBias(window);
            window.release();
        }

        // now hand off to the L1 processor.
        mExposureBlock.mL1Processor.submitFrame(mFrame);
    }
}
