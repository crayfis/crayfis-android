package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import java.util.HashMap;
import java.util.Map;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

public class L2Processor {
    public static final Map<String, L2TriggerType> L2_TRIGGER_TYPE_MAP = new HashMap<String, L2TriggerType>()  {
        {
            put("default", L2TriggerType.DEFAULT);
            put("maxn", L2TriggerType.MAXN);
        }
    };

    public enum L2TriggerType {
        DEFAULT, MAXN,
    }

    public final CFApplication mApplication;

    public int mL2Count = 0;

    public L2Processor(CFApplication application) {
        // TODO: it will make more sense to have the triggerType assigned to the XB and then get it from there.
        mApplication = application;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        Runnable task;
        switch (frame.getExposureBlock().L2_trigger_type) {
            case DEFAULT:
                task = new L2Task(frame, this);
                break;
            case MAXN:
                task = new L2TaskMaxN(frame, this);
                CFLog.i("Creating a MAXN l2 task!!!!");
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