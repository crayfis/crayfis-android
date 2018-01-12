package io.crayfis.android.trigger.L2;

import java.util.HashMap;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.trigger.calibration.FrameHistory;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.util.CFLog;

public class L2Processor extends TriggerProcessor {

    public static int L2Count = 0;

    private static final int PASS_TIME_CAPACITY = 25;
    private static final FrameHistory<Long> sPassTimes = new FrameHistory<>(PASS_TIME_CAPACITY);

    public L2Processor(CFApplication application, String configStr) {
        super(application, configStr, false);
    }

    @Override
    public Config makeConfig(String name, HashMap<String, String> options) {

        switch (name) {
            case "default":
                return new L2Task.Config(name, options);
            case "maxn":
                return new L2TaskMaxN.Config(name, options);
            case "byteblock":
                return new L2TaskByteBlock.Config(name, options);
            default:
                CFLog.w("No L2 implementation found for " + name + ", using default!");
                return new L2Task.Config(name, options);
        }
    }

    @Override
    public void submitFrame(RawCameraFrame frame) {
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
                return 0.0;
            }
            long dt = System.nanoTime() - CFApplication.getStartTimeNano() - sPassTimes.getOldest();
            double dtMin = dt / 1000000000. / 60.;
            return sPassTimes.size() / dtMin;
        }
    }
}