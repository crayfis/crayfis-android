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
    public final L1Config mL1Config;
    public final int mL1Thresh;

    L1Calibrator mL1Cal;
    PreCalibrator mPreCal;
    public static int mL1Count = 0;
    public static int mL1CountData;

    int mBufferBalance = 0;

    L2Processor mL2Processor = null;

    final CFConfig CONFIG = CFConfig.getInstance();

    public L1Processor(CFApplication application, L1Config config, int l1thresh) {
        mApplication = application;
        mL1Config = config;
        mL1Thresh = l1thresh;
        mL1Cal = L1Calibrator.getInstance();
        mPreCal = PreCalibrator.getInstance(application);
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return mL1Config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }
}
