package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.calibration.PreCalibrator;
import edu.uci.crayfis.camera.RawCameraFrame;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor {

    public final CFApplication mApplication;

    L1Calibrator mL1Cal;
    PreCalibrator mPreCal;
    public static int mL1Count = 0;
    public static int mL1CountData;

    public int mBufferBalance = 0;

    public L2Processor mL2Processor = null;

    public final CFConfig CONFIG = CFConfig.getInstance();

    public L1Processor(CFApplication application) {
        mApplication = application;
        mL2Processor = new L2Processor(mApplication);
        mL1Cal = L1Calibrator.getInstance();
        mPreCal = PreCalibrator.getInstance();
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return frame.getExposureBlock().L1_trigger_config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}
