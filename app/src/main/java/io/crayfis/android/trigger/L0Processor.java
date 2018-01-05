package io.crayfis.android.trigger;


import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.fabric.sdk.android.services.concurrency.AsyncTask;

/**
 * Created by cshimmin on 1/4/18.
 */

public class L0Processor {
    final CFApplication mApplication;

    public static int mL0Count = 0;
    public static int mL0CountData;

    int mBufferBalance = 0;

    L1Processor mL1Processor = null;

    final CFConfig CONFIG = CFConfig.getInstance();

    public L0Processor(CFApplication application) {
        mApplication = application;
        mL1Processor = new L1Processor(application);
    }

    private Runnable makeTask(RawCameraFrame frame) {
        return frame.getExposureBlock().getL0Config().makeTask(this, frame);
    }

    public void submitFrame(RawCameraFrame frame) {
        mBufferBalance++;
        // Note: we use a serial_executor here so that the zerobias processor
        // sees frames in the order in which they are received.
        AsyncTask.SERIAL_EXECUTOR.execute(makeTask(frame));
    }
}
