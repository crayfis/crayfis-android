package edu.uci.crayfis.exposure;

import java.util.ArrayList;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.Iterator;
import java.util.LinkedList;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.camera.CFCamera;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.util.CFLog;

/**
 * Exposure block manager.
 */
public final class ExposureBlockManager {

    // max amount of time to wait before considering an retired but un-finalized XB to be "stale";
    // after this time, it will be uploaded regardless. [ms]
    public final int XB_STALE_TIME = 30000;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private final CFApplication APPLICATION;

    // grace period relative to the last known "safe time" to allow XB's after they're closed
    // before they are submitted for upload [ms]
    public final long SAFE_TIME_BUFFER = 2500;

    private int mTotalXBs = 0;
    private int mCommittedXBs = 0;

    private final Handler mFlushHandler;

    // This is where the current xb is kept. The DAQActivity must access
    // the current exposure block through the public methods here.
    private ExposureBlock current_xb;

    // We keep a list of retired blocks, which have been closed but
    // may not be ready to commit yet (e.g. events belonging to this block
    // might be sequested in a queue somewhere, still)
    private LinkedList<ExposureBlock> retired_blocks = new LinkedList<ExposureBlock>();

    private long safe_time = 0;

    private static ExposureBlockManager sInstance;

    /**
     * Get the instance of {@link ExposureBlockManager}.
     *
     * @param context The context.
     * @return {@link ExposureBlockManager}
     */
    public static synchronized ExposureBlockManager getInstance(@NonNull final Context context) {
        if (sInstance == null) {
            sInstance = new ExposureBlockManager(context);
        }
        return sInstance;
    }

    private ExposureBlockManager(@NonNull final Context context) {
        APPLICATION = (CFApplication) context.getApplicationContext();

        mFlushHandler = new Handler();
    }

    // Atomically check whether the current XB is to old, and if so,
    // create a new one. Then return the current block in either case.
    public synchronized ExposureBlock getCurrentExposureBlock() {
        CFApplication.State app_state = APPLICATION.getApplicationState();
        if (current_xb == null) {
            // FIXME Had to add this call after creating the state broadcast, timing error?
            CFLog.e("Oops! In getCurrentExposureBlock(), current_xb = null");
            newExposureBlock(app_state);
        }

        // if we are in (pre)calibration mode, keep the XB running until calibration is complete.
        else if ((app_state == CFApplication.State.DATA) && (current_xb.daq_state == CFApplication.State.DATA)
                && current_xb.nanoAge() > ((long) CONFIG.getExposureBlockPeriod() * 1000000000L)) {

            newExposureBlock(app_state);
        }

        return current_xb;
    }

    public synchronized void newExposureBlock(CFApplication.State state) {

        CFCamera camera = CFCamera.getInstance();

        if(camera.getResX() == 0) {
            // camera error -- don't crash
            return;
        }

        if (current_xb != null) {
            current_xb.freeze();
            retireExposureBlock(current_xb);
        }

        int cameraId = camera.getCameraId();

        CFLog.i("Starting new exposure block w/ state " + state + "! (" + retired_blocks.size() + " retired blocks queued.)");
        current_xb = new ExposureBlock(mTotalXBs,
                APPLICATION.getBuildInformation().getRunId(),
                cameraId == -1 ? null : CONFIG.getPrecalId(cameraId),
                CONFIG.getL1Trigger(),
                CONFIG.getL2Trigger(),
                CONFIG.getL1Threshold(), CONFIG.getL2Threshold(),
                camera.getLastKnownLocation(),
                CFApplication.getBatteryTemp(),
                state, camera.getResX(), camera.getResY());

        // start assigning frames to new xb
        camera.getFrameBuilder().setExposureBlock(current_xb);

        mTotalXBs++;

        scheduleFlush();
    }

    private void scheduleFlush() {
        scheduleFlush(1000);
    }
    private void scheduleFlush(long delay) {
        // set a background thread to call flushCommittedBlocks() (e.g. so that we don't have
        // to do it in a trigger or UI thread.
        mFlushHandler.postDelayed(new Runnable() {
                                      @Override
                                      public void run() {
                                          flushCommittedBlocks();
                                      }
                                  },
                delay);
    }

    public synchronized void abortExposureBlock() {
        if(current_xb != null) {
            current_xb.aborted = true;
        }
        newExposureBlock(APPLICATION.getApplicationState());
    }

    private void retireExposureBlock(ExposureBlock xb) {
        // anything that's being committed must have already been frozen.
        assert xb.isFrozen();

        if (xb.daq_state != CFApplication.State.INIT && xb.daq_state != CFApplication.State.CALIBRATION && xb.daq_state != CFApplication.State.DATA) {
            CFLog.e("Received ExposureBlock with a state of " + xb.daq_state + ", ignoring.");
            return;
        }

        retired_blocks.add(xb);
    }

    public void updateSafeTime(long time) {
        // this time should be monotonically increasing
        assert time >= safe_time;
        safe_time = time;
    }

    public void flushCommittedBlocks() {
        // Try to flush out any committed exposure blocks that
        // have no new events coming.
        if (retired_blocks.size() == 0) {
            // nothing to be done.
            return;
        }

        ArrayList<ExposureBlock> toRemove = new ArrayList<>();
        long current_time = System.nanoTime() - CFApplication.getStartTimeNano();
        synchronized (retired_blocks) {
            for (Iterator<ExposureBlock> it = retired_blocks.iterator(); it.hasNext(); ) {
                ExposureBlock xb = it.next();
                /*if (xb.end_time.Nano < (safe_time - SAFE_TIME_BUFFER*1000000L)) {*/
                if (xb.isFinalized()) {
                    // okay, it's safe to commit this block now. add it to the list of XBs
                    // to dispatch, and then do that outside the synch block.
                    toRemove.add(xb);
                    it.remove();
                } else if ( (current_time - xb.end_time.Nano) > XB_STALE_TIME * 1000000L) {
                    // this XB has gone stale! we should upload it anyways.
                    CFLog.w("Stale XB detected! Uploading even though not finalized.");
                    toRemove.add(xb);
                    it.remove();
                }
            }
        }

        for (ExposureBlock xb : toRemove) {
            // submit the retired XB's to be uploaded.
            UploadExposureService.submitExposureBlock(APPLICATION, xb);
        }
    }

    public void flushCommittedBlocks(boolean force) {
        // If force == true, immediately flush all blocks.
        if (force) {
            updateSafeTime(System.nanoTime()-APPLICATION.getStartTimeNano());
        }
        flushCommittedBlocks();
    }

    private void commitExposureBlock(ExposureBlock xb) {
        if (xb.daq_state == CFApplication.State.STABILIZATION
                || xb.daq_state == CFApplication.State.IDLE || xb.daq_state == CFApplication.State.RECONFIGURE) {
            // don't commit stabilization/idle blocks! they're just deadtime.
            return;
        }
        if (xb.daq_state == CFApplication.State.CALIBRATION
                && xb.aborted) {
            // also, don't commit *aborted* calibration blocks
            return;
        }

        CFLog.i("DAQActivity Commiting old exposure block!");
        UploadExposureService.submitExposureBlock(APPLICATION, xb);
        mCommittedXBs++;
    }

    public int getTotalXBs() {
        return mTotalXBs;
    }

    public int getCommittedXBs() {
        return mCommittedXBs;
    }
}
