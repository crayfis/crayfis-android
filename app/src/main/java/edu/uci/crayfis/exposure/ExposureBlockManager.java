package edu.uci.crayfis.exposure;

import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;

import java.util.Iterator;
import java.util.LinkedList;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.OutputManager;
import edu.uci.crayfis.util.CFLog;

/**
 * Exposure block manager.
 */
public final class ExposureBlockManager {

    private final CFConfig CONFIG = CFConfig.getInstance();
    private final OutputManager OUTPUT_MANAGER;
    private final CFApplication APPLICATION;

    private int mTotalXBs = 0;
    private int mCommittedXBs = 0;

    // This is where the current xb is kept. The DAQActivity must access
    // the current exposure block through the public methods here.
    private ExposureBlock current_xb;

    // We keep a list of retired blocks, which have been closed but
    // may not be ready to commit yet (e.g. events belonging to this block
    // might be sequested in a queue somewhere, still)
    private LinkedList<ExposureBlock> retired_blocks = new LinkedList<ExposureBlock>();

    private long safe_time = 0;

    private long committed_exposure;

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
        OUTPUT_MANAGER = OutputManager.getInstance(APPLICATION);
    }

    // Atomically check whether the current XB is to old, and if so,
    // create a new one. Then return the current block in either case.
    public synchronized ExposureBlock getCurrentExposureBlock() {
        if (current_xb == null) {
// FIXME Had to add this call after creating the state broadcast, timing error?
            newExposureBlock();
        }

        // check and see whether this XB is too old
        if (current_xb.age() > CONFIG.getExposureBlockPeriod() * 1000) {
            newExposureBlock();
        }

        return current_xb;
    }

    // Return an estimate of the exposure time in committed + current
    // exposure blocks.
    public synchronized long getExposureTime() {
// FIXME Had to add this null check after creating the state broadcast, timing error?
        if (current_xb != null && current_xb.daq_state == CFApplication.State.DATA) {
            return committed_exposure + current_xb.age();
        } else {
            return committed_exposure;
        }
    }

    public synchronized void newExposureBlock() {
        if (current_xb != null) {
            current_xb.freeze();
            retireExposureBlock(current_xb);
        }

        CFLog.i("DAQActivity Starting new exposure block!");
        current_xb = new ExposureBlock();
        mTotalXBs++;

        current_xb.xbn = mTotalXBs;
        current_xb.L1_thresh = CONFIG.getL1Threshold();
        current_xb.L2_thresh = CONFIG.getL2Threshold();
        current_xb.start_loc = new Location(CFApplication.getLastKnownLocation());
        current_xb.daq_state = APPLICATION.getApplicationState();
        current_xb.run_id = APPLICATION.getBuildInformation().getRunId();
    }

    public synchronized void abortExposureBlock() {
        current_xb.aborted = true;
        newExposureBlock();
    }

    private void retireExposureBlock(ExposureBlock xb) {
        // anything that's being committed must have already been frozen.
        assert xb.frozen;

        if (xb.daq_state != CFApplication.State.INIT && xb.daq_state != CFApplication.State.CALIBRATION && xb.daq_state != CFApplication.State.DATA) {
            CFLog.e("Received ExposureBlock with a state of " + xb.daq_state + ", ignoring.");
            return;
        }

        retired_blocks.add(xb);

        // if this is a DATA block, add its age to the commited
        // exposure time.
        if (xb.daq_state == CFApplication.State.DATA) {
            committed_exposure += xb.age();
        }
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

        for (Iterator<ExposureBlock> it = retired_blocks.iterator(); it.hasNext(); ) {
            ExposureBlock xb = it.next();
            if (xb.end_time < safe_time) {
                // okay, it's safe to commit this block now.
                it.remove();
                OUTPUT_MANAGER.commitExposureBlock(xb);
            }
        }
    }

    public void flushCommittedBlocks(boolean force) {
        // If force == true, immediately flush all blocks.
        if (force) {
            updateSafeTime(System.currentTimeMillis());
        }
        flushCommittedBlocks();
    }

    private void commitExposureBlock(ExposureBlock xb) {
        if (xb.daq_state == CFApplication.State.STABILIZATION
                || xb.daq_state == CFApplication.State.IDLE) {
            // don't commit stabilization/idle blocks! they're just deadtime.
            return;
        }
        if (xb.daq_state == CFApplication.State.CALIBRATION
                && xb.aborted) {
            // also, don't commit *aborted* calibration blocks
            return;
        }

        CFLog.i("DAQActivity Commiting old exposure block!");
        final boolean success = OUTPUT_MANAGER.commitExposureBlock(xb);

        if (!success) {
            // Oops! The output manager's queue must be full!
            throw new RuntimeException("Oh no! Couldn't commit an exposure block. What to do?");
        }

        mCommittedXBs++;
    }

    public int getTotalXBs() {
        return mTotalXBs;
    }

    public int getCommittedXBs() {
        return mCommittedXBs;
    }
}
