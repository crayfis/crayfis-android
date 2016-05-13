package edu.uci.crayfis.trigger;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.trigger.L1Task;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor {

    public enum L1TriggerType {
        DEFAULT,
    }

    public final CFApplication mApplication;
    private boolean mRecycle = false;

    public L1Calibrator mL1Cal = null;
    public int mL1Count = 0;
    public int mCalibrationCount = 0;
    public int mStabilizationCount = 0;

    public int mBufferBalance = 0;

    public L2Processor mL2Processor = null;

    public final CFConfig CONFIG = CFConfig.getInstance();

    public L1TriggerType mTriggerType = null;

    public L1Processor(L1TriggerType triggerType, CFApplication application, boolean recycle) {
        mApplication = application;
        mTriggerType = triggerType;
        mRecycle = recycle;

        mL1Cal = L1Calibrator.getInstance();
    }

    public void setL2Processor(L2Processor l2) {
        mL2Processor = l2;
    }

    private Runnable makeTask(RawCameraFrame frame, Camera camera) {
        Runnable task = null;
        switch (mTriggerType) {
            case DEFAULT:
                task = new L1Task(this, frame, camera);
                break;
            default:
                CFLog.e("Unimplemented trigger type selected!! Falling back to default.");
                task = new L1Task(this, frame, camera);
                break;
        }
        return task;
    }

    public void submitFrame(RawCameraFrame frame, Camera camera) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame, camera));
    }
}
