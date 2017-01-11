package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.camera.RawCameraFrame;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor {

    public final CFApplication mApplication;

    public L1Calibrator mL1Cal = null;
    public int mL1Count = 0;
    public int mL1CountData;

    public int mBufferBalance = 0;

    public L2Processor mL2Processor = null;

    public final CFConfig CONFIG = CFConfig.getInstance();

    public L1Processor(CFApplication application) {
        mApplication = application;

        mL1Cal = L1Calibrator.getInstance();
    }

    public void setL2Processor(L2Processor l2) {
        mL2Processor = l2;
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return frame.getExposureBlock().L1_trigger_config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}
