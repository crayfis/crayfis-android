package edu.uci.crayfis.trigger;

import android.os.AsyncTask;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.precalibration.PreCalibrator;
import edu.uci.crayfis.camera.frame.RawCameraFrame;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor {

    final CFApplication mApplication;

    L1Calibrator mL1Cal;
    PreCalibrator mPreCal;
    public static int mL1Count = 0;
    public static int mL1CountData;

    int mBufferBalance = 0;

    L2Processor mL2Processor = null;

    final CFConfig CONFIG = CFConfig.getInstance();

    public L1Processor(CFApplication application) {
        mApplication = application;
        mL2Processor = new L2Processor(application);
        mL1Cal = L1Calibrator.getInstance();
        mPreCal = PreCalibrator.getInstance(application);
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return frame.getExposureBlock().L1_trigger_config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}
