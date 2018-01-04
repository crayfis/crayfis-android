package io.crayfis.android.trigger;

import android.os.AsyncTask;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.calibration.L1Calibrator;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.camera.RawCameraFrame;

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
        return frame.getExposureBlock().getL1Config().makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}
