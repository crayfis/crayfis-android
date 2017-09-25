package io.crayfis.android.trigger;

import android.os.AsyncTask;

import io.crayfis.android.CFApplication;
import io.crayfis.android.calibration.FrameHistory;
import io.crayfis.android.calibration.Histogram;
import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.util.CFLog;

public class L2Processor {
    final CFApplication mApplication;

    public static int mL2Count = 0;

    private static FrameHistory<Long> mPassTimes = new FrameHistory<>(25);

    public static Histogram histL2Pixels = new Histogram(256);

    L2Processor(CFApplication application) {
        // TODO: it will make more sense to have the triggerType assigned to the XB and then get it from there.
        mApplication = application;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return frame.getExposureBlock().L2_trigger_config.makeTask(this, frame);
    }

    void submitFrame(RawCameraFrame frame) {
        // the frame ought to be claimed by the L1 stage by the time it gets here!
        assert !frame.isOutstanding();

        // record the frame time to calculate pass rate
        mPassTimes.addValue(frame.getAcquiredTimeNano());

        // send it off to be processed by L2 logic
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }

    /**
     * Calculates and returns L1 pass rate over the last 25 passes
     * @return Pass rate, in frames per minute
     */
    public static double getPassRateFPM() {
        if(mPassTimes.size() == 0) {
            return 0.0;
        }
        long dt = System.nanoTime() - CFApplication.getStartTimeNano() - mPassTimes.getOldest();
        double dtMin = dt / 1000000000. / 60.;
        return mPassTimes.size() / dtMin;
    }
}