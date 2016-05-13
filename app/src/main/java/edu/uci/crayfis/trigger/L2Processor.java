package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlockManager;

public class L2Processor {
    public enum L2TriggerType {
        DEFAULT,
    }

    private final ExposureBlockManager XB_MANAGER;

    public L2TriggerType mTriggerType = null;
    public final CFApplication mApplication;

    public int mL2Count = 0;

    public L2Processor(L2TriggerType triggerType, CFApplication application) {
        mTriggerType = triggerType;
        mApplication = application;

        // TODO: does the CFApplication cast back to the identical Context used elsewhere?
        XB_MANAGER = ExposureBlockManager.getInstance(application);
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return new L2Task(frame, this);
    }

    public void submitFrame(RawCameraFrame frame) {
        // the frame ought to be claimed by the L1 stage by the time it gets here!
        assert !frame.isOutstanding();

        // If we made it this far, we have a real data frame ready to go.
        // First update the XB manager's safe time (so it knows which old XB
        // it can commit.
        XB_MANAGER.updateSafeTime(frame.getAcquiredTimeNano());

        // send it off to be processed by L2 logic
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}