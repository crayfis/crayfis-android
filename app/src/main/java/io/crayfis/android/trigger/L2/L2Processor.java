package io.crayfis.android.trigger.L2;

import java.util.HashMap;

import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.FrameHistory;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.util.CFLog;

public class L2Processor extends TriggerProcessor {

    public static final String KEY_NPIX = "npix";
    public static final String KEY_RADIUS = "radius";
    public static final String KEY_L2_THRESH = "l2thresh";
    public static final String KEY_MAXN = "maxn";

    public static int L2Count = 0;

    private static final int PASS_TIME_CAPACITY = 25;
    private static final FrameHistory<Long> sPassTimes = new FrameHistory<>(PASS_TIME_CAPACITY);

    private L2Processor(CFApplication application, ExposureBlock xb, TriggerProcessor.Config config) {
        super(application, xb, config, false);
    }

    public static TriggerProcessor makeProcessor(CFApplication application, ExposureBlock xb) {
        return new L2Processor(application, xb, CFConfig.getInstance().getL2Trigger());
    }

    /**
     * Derived Config class to generate L2 thresholds from L1
     */
    public static abstract class Config extends TriggerProcessor.Config {

        public Config(String taskName, HashMap<String, String> keyVal, HashMap<String, Object> keyDefault) {
            super(taskName, keyVal, keyDefault);
        }

        public abstract int generateL2Threshold(float l1Thresh);
    }

    public static Config makeConfig(String configStr) {

        HashMap<String, String> options = TriggerProcessor.parseConfigString(configStr);
        String name = options.get("name");
        options.remove("name");

        switch (name) {
            case L2TaskPixels.Config.NAME:
                return new L2TaskPixels.Config(options);
            case L2TaskByteBlock.Config.NAME:
                return new L2TaskByteBlock.Config(options);
            default:
                CFLog.w("No L2 implementation found for " + name + ", using default!");
                return new L2TaskByteBlock.Config(options);
        }
    }

    @Override
    public void submitFrame(Frame frame) {
        super.submitFrame(frame);

        // record the frame time to calculate pass rate
        synchronized (sPassTimes) {
            sPassTimes.addValue(frame.getAcquiredTimeNano());
        }
    }

    /**
     * Calculates and returns L1 pass rate over the last 25 passes
     * @return Pass rate, in frames per minute
     */
    public static double getPassRateFPM() {
        synchronized (sPassTimes) {
            if (sPassTimes.size() < PASS_TIME_CAPACITY) {
                return -1.0;
            }
            long dt = System.nanoTime() - sPassTimes.getOldest();
            double dtMin = dt / 1000000000. / 60.;
            return sPassTimes.size() / dtMin;
        }
    }

    /**
     * Clear pass rate statistics when an ExposureBlock is aborted
     */
    public static void resetPassRate() {
        synchronized (sPassTimes) {
            sPassTimes.clear();
        }
    }
}