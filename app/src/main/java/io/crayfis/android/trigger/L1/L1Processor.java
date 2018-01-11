package io.crayfis.android.trigger.L1;

import android.os.AsyncTask;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.calibration.L1Calibrator;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.exposure.frame.RawCameraFrame;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor {

    final CFApplication mApplication;
    private final L1Config mL1Config;
    public final int mL1Thresh;

    public int processed = 0;
    public int pass = 0;
    public int skip = 0;
    public static int L1Count = 0;
    public static int L1CountData;

    L1Calibrator mL1Cal;
    PreCalibrator mPreCal;

    int mBufferBalance = 0;

    final CFConfig CONFIG = CFConfig.getInstance();

    public L1Processor(CFApplication application, String configStr, int l1thresh) {
        mApplication = application;
        mL1Config = L1Config.makeConfig(configStr);
        mL1Thresh = l1thresh;
        mL1Cal = L1Calibrator.getInstance();
        mPreCal = PreCalibrator.getInstance(application);
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return mL1Config.makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        processed++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(makeTask(frame));
    }

    public String getConfig() {
        return mL1Config.toString();
    }
}
