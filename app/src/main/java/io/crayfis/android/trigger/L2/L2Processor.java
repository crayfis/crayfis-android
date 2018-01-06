package io.crayfis.android.trigger.L2;

import android.os.AsyncTask;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.L1.calibration.FrameHistory;
import io.crayfis.android.camera.RawCameraFrame;

public class L2Processor {
    final CFApplication mApplication;
    private final L2Config mL2Config;
    public final int mL2Thresh;

    public static int mL2Count = 0;

    private static final int PASS_TIME_CAPACITY = 25;
    private static final FrameHistory<Long> sPassTimes = new FrameHistory<>(PASS_TIME_CAPACITY);

    public L2Processor(CFApplication application, String configStr, int l2thresh) {
        mApplication = application;
        mL2Config = L2Config.makeConfig(configStr);
        mL2Thresh = l2thresh;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return mL2Config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        // the frame ought to be claimed by the L1 stage by the time it gets here!
        assert !frame.isOutstanding();

        // record the frame time to calculate pass rate
        synchronized (sPassTimes) {
            sPassTimes.addValue(frame.getAcquiredTimeNano());
        }

        // send it off to be processed by L2 logic
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
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

    public String getConfig() {
        return mL2Config.toString();
    }
}