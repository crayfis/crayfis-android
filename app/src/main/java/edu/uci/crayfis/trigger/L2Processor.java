package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.calibration.Histogram;
import edu.uci.crayfis.camera.RawCameraFrame;

public class L2Processor {
    public final CFApplication mApplication;

    public static int mL2Count = 0;

    public static Histogram histL2Pixels = new Histogram(256);

    public L2Processor(CFApplication application) {
        // TODO: it will make more sense to have the triggerType assigned to the XB and then get it from there.
        mApplication = application;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return frame.getExposureBlock().L2_trigger_config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        // the frame ought to be claimed by the L1 stage by the time it gets here!
        assert !frame.isOutstanding();

        // send it off to be processed by L2 logic
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}