package edu.uci.crayfis.trigger;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.AsyncTask;

import java.util.HashMap;
import java.util.Map;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.exposure.ExposureBlockManager;
import edu.uci.crayfis.trigger.L1Task;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor {

    public static final Map<String, L1TriggerType> L1_TRIGGER_TYPE_MAP = new HashMap<String, L1TriggerType>()  {
        {
            put("default", L1TriggerType.DEFAULT);
        }
    };

    public enum L1TriggerType {
        DEFAULT,
    }

    public final CFApplication mApplication;

    public L1Calibrator mL1Cal = null;
    public int mL1Count = 0;
    public int mCalibrationCount = 0;
    public int mStabilizationCount = 0;

    public int mBufferBalance = 0;

    public L2Processor mL2Processor = null;

    public final CFConfig CONFIG = CFConfig.getInstance();

    public L1TriggerType mTriggerType = null;

    public L1Processor(CFApplication application) {
        mApplication = application;

        mL1Cal = L1Calibrator.getInstance();
    }

    public void setL2Processor(L2Processor l2) {
        mL2Processor = l2;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        Runnable task = null;
        switch (frame.getExposureBlock().L1_trigger_type) {
            case DEFAULT:
                task = new L1Task(this, frame);
                break;
            default:
                CFLog.e("Unimplemented trigger type selected!! Falling back to default.");
                task = new L1Task(this, frame);
                break;
        }
        return task;
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}
