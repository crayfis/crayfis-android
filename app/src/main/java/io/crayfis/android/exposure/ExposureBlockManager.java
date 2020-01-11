package io.crayfis.android.exposure;

import java.util.ArrayList;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;

import java.util.LinkedHashSet;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.server.PreCalibrationService;
import io.crayfis.android.trigger.TriggerChain;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.util.CFLog;

/**
 * Exposure block manager.
 */
public final class ExposureBlockManager {

    // max amount of time to wait before considering an retired but un-finalized XB to be "stale";
    // after this time, it will be uploaded regardless. [ms]
    private static final int XB_STALE_TIME = 30000;
    private static final long PASS_RATE_CHECK_TIME = 5000L;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication mApplication;

    private int mTotalXBs = 0;

    private Handler mXBHandler;
    private final HandlerThread mXBThread = new HandlerThread("XBManager");

    // This is where the current xb is kept. The DAQActivity must access
    // the current exposure block through the public methods here.
    private ExposureBlock current_xb;

    // We keep a list of retired blocks, which have been closed but
    // may not be ready to commit yet (e.g. events belonging to this block
    // might be sequested in a queue somewhere, still)
    private final LinkedHashSet<ExposureBlock> retired_blocks = new LinkedHashSet<>();

    private XBExpirationTimer mXBExpirationTimer;

    // timer for creating new DATA blocks
    private class XBExpirationTimer extends CountDownTimer {

        XBExpirationTimer() {
            super(CONFIG.getExposureBlockPeriod()*1000L, PASS_RATE_CHECK_TIME);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            // do nothing the first time
            if(CONFIG.getExposureBlockPeriod() - millisUntilFinished < PASS_RATE_CHECK_TIME) {
                return;
            }
            // check whether the threshold has drifted
            double passRate = L2Processor.getPassRateFPM();
            if(passRate > 1.5 * CONFIG.getTargetEventsPerMinute()) {
                abortExposureBlock();
            }

        }

        @Override
        public void onFinish() {
            newExposureBlock(CFApplication.State.DATA);
        }
    }

    private static ExposureBlockManager sInstance;

    /**
     * Get the instance of {@link ExposureBlockManager}.
     *
     * @return {@link ExposureBlockManager}
     */
    public static synchronized ExposureBlockManager getInstance() {
        if (sInstance == null) {
            sInstance = new ExposureBlockManager();
        }
        return sInstance;
    }

    private ExposureBlockManager() { }
    
    public void register(@NonNull final CFApplication app) {
        mApplication = app;

        mXBThread.start();
        mXBHandler = new Handler(mXBThread.getLooper());
    }

    public ExposureBlock getCurrentExposureBlock() {
        return current_xb;
    }

    public void newExposureBlock(final CFApplication.State state) {

        mXBHandler.post(new Runnable() {
            @Override
            public void run() {
                CFCamera camera = CFCamera.getInstance();

                // need to check battery BEFORE freezing to set final battery temp
                mApplication.checkBatteryStats();

                // set a timer for when this XB expires, if we are in DATA mode
                if(mXBExpirationTimer != null) {
                    mXBExpirationTimer.cancel();
                }
                if(state == CFApplication.State.DATA) {
                    mXBExpirationTimer = new XBExpirationTimer();
                    mXBExpirationTimer.start();
                }

                PreCalibrationService.Config precalConfig = CFConfig.getInstance().getPrecalConfig();

                CFLog.i("Starting new exposure block w/ state " + state + "! (" + retired_blocks.size() + " retired blocks queued.)");
                ExposureBlock newXB = new ExposureBlock(mApplication,
                        mTotalXBs,
                        mApplication.getBuildInformation().getRunId(),
                        precalConfig != null ?
                                precalConfig.getHotHash() : -1,
                        precalConfig != null ?
                                precalConfig.getWeightHash() : -1,
                        camera.getCameraId(),
                        camera.isFacingBack(),
                        precalConfig != null ?
                                precalConfig.getScriptCWeight(mApplication.getRenderScript())
                                : null,
                        new TriggerChain(mApplication, state),
                        camera.getLastKnownLocation(),
                        mApplication.getBatteryTemp(),
                        state,
                        camera.getResX(),
                        camera.getResY());

                // start assigning frames to new xb
                camera.setExposureBlock(newXB);

                if (current_xb != null) {
                    current_xb.freeze();
                    retireExposureBlock(current_xb);
                }

                current_xb = newXB;

                mTotalXBs++;

                flushCommittedBlocks(1000);
            }
        });
    }

    public void flushCommittedBlocks() {
        flushCommittedBlocks(0);
    }

    public void flushCommittedBlocks(long delay) {

        mXBHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Try to flush out any committed exposure blocks that
                // have no new events coming.
                if (retired_blocks.size() == 0) {
                    // nothing to be done.
                    return;
                }

                ArrayList<ExposureBlock> toRemove = new ArrayList<>();
                long current_time = System.nanoTime();
                synchronized (retired_blocks) {
                    for (ExposureBlock xb : retired_blocks) {
                        if (xb.isFinalized()) {
                            // okay, it's safe to commit this block now. add it to the list of XBs
                            // to dispatch, and then do that outside the sync block.
                            toRemove.add(xb);
                        } else if ( (current_time - xb.getEndTimeNano()) > XB_STALE_TIME * 1000000L) {
                            // this XB has gone stale! we should upload it anyways.
                            CFLog.w("Stale XB detected! Uploading even though not finalized.");
                            toRemove.add(xb);
                        }
                    }
                }

                for (ExposureBlock xb : toRemove) {
                    // submit the retired XB's to be uploaded.
                    UploadExposureService.submitMessage(mApplication, xb.camera_id, xb.buildProto());
                    retired_blocks.remove(xb);
                }
            }
        }, delay);
    }

    public synchronized void abortExposureBlock() {
        if(current_xb != null) {
            current_xb.aborted = true;
        }
        newExposureBlock(mApplication.getApplicationState());
    }

    private void retireExposureBlock(ExposureBlock xb) {
        // anything that's being committed must have already been frozen.

        CFApplication.State state = xb.daq_state;

        switch (state) {
            case INIT:
            case SURVEY:
            case IDLE:
            case FINISHED:
                CFLog.e("Received ExposureBlock with a state of " + state + ", ignoring.");
                break;
            case PRECALIBRATION:
            case CALIBRATION:
            case DATA:
                retired_blocks.add(xb);
        }
    }

    public int getTotalXBs() {
        return mTotalXBs;
    }

    /**
     * Make sure we create a new instance in future runs
     */
    public void unregister() {
        mXBThread.quitSafely();
        try {
            mXBThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sInstance = null;
    }
}
