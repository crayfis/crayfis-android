package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

public class L2Processor {
    public enum L2TriggerType {
        DEFAULT,
    }

    public L2TriggerType mTriggerType = null;
    public final CFApplication mApplication;

    public int mL2Count = 0;

    public L2Processor(L2TriggerType triggerType, CFApplication application) {
        // TODO: it will make more sense to have the triggerType assigned to the XB and then get it from there.
        mTriggerType = triggerType;
        mApplication = application;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        Runnable task;
        switch (mTriggerType) {
            case DEFAULT:
                task = new L2Task(frame, this);
                break;
            default:
                CFLog.w("Unimplemented L2 trigger type selected! Falling back to default.");
                task = new L2Task(frame, this);
                break;
        }
        return task;
    }

    public void submitFrame(RawCameraFrame frame) {
        // the frame ought to be claimed by the L1 stage by the time it gets here!
        assert !frame.isOutstanding();

        // send it off to be processed by L2 logic
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}