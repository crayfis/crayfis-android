package edu.uci.crayfis.trigger;

import android.graphics.ImageFormat;
import android.hardware.Camera;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
class L1Task implements Runnable {
    private L1Processor mL1Processor = null;
    private L2Processor mL2Processor = null;
    private RawCameraFrame mFrame = null;
    private ExposureBlock mExposureBlock = null;
    private CFApplication mApplication = null;
    private boolean mKeepFrame = false;

    public L1Task(L1Processor l1processor, RawCameraFrame frame) {
        mL1Processor = l1processor;
        mFrame = frame;
        mExposureBlock = mFrame.getExposureBlock();

        mApplication = mL1Processor.mApplication;
        mL2Processor = mL1Processor.mL2Processor;
    }



    protected boolean processInitial() {
        // show the frame to the L1 calibrator
        mL1Processor.mL1Cal.AddFrame(mFrame);

        return false;
    }

    protected boolean processCalibration() {
        // if we are in (L1) calibration mode, there's no need to do anything else with this
        // frame; the L1 calibrator already saw it. Just check to see if we're done calibrating.
        long count = ++mExposureBlock.calibration_count;

        if (count == mL1Processor.CONFIG.getCalibrationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.DATA);
        }

        return true;
    }

    protected boolean processStabilization() {
        // If we're in stabilization mode, just drop frames until we've skipped enough
        long count = ++mExposureBlock.stabilization_count;
        if (count == mL1Processor.CONFIG.getStabilizationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
        return true;
    }

    protected boolean processIdle() {
        // Not sure why we're still acquiring frames in IDLE mode...
        CFLog.w("DAQActivity Frames still being received in IDLE mode");
        return true;
    }

    protected boolean processData() {
        // check if we pass the L1 threshold
        boolean pass = false;

        int max = mFrame.getPixMax();

        mExposureBlock.total_background += mFrame.getPixAvg();
        mExposureBlock.total_max += mFrame.getPixMax();

        if (max > mExposureBlock.L1_threshold) {
            // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
            // have changed.
            pass = true;
        }

        if (pass) {
            mExposureBlock.L1_pass++;

            mKeepFrame = true;

            // this frame has passed the L1 threshold, put it on the
            // L2 processing queue.
            mL2Processor.submitFrame(mFrame);
        } else {
            // didn't pass. recycle the buffer.
            mExposureBlock.L1_skip++;
        }

        return false;
    }

    protected void processFinal() {
        return;
    }

    private void processFrame() {
        if (processInitial()) { return; }

        boolean stopProcessing;
        switch (mExposureBlock.daq_state) {
            case CALIBRATION:
                stopProcessing = processCalibration();
                break;
            case STABILIZATION:
                stopProcessing = processStabilization();
                break;
            case IDLE:
                stopProcessing = processIdle();
                break;
            case DATA:
                stopProcessing = processData();
                break;
            default:
                CFLog.w("Unimplemented state encountered in processFrame()! Dropping frame.");
                stopProcessing = true;
                break;
        }

        if (stopProcessing) {
            return;
        }

        processFinal();
    }

    @Override
    public void run() {
        ++mL1Processor.mL1Count;

        processFrame();

        if (mKeepFrame) {
            // this frame has been passed on to L2. claim the buffer for now.

            // add a new buffer to the queue to make up for this one which
            // will not return
            // TODO: make sure we check that there's enough memory to allocate a frame!
            mFrame.claim();
        } else {
            // we are done with this frame. retire the buffer and also clear it from the XB.
            mFrame.retire();
            mFrame.clear();
        }

        if (mFrame.isOutstanding()) {
            CFLog.w("Frame still outstanding after running L1Task!");
        } else {
            mL1Processor.mBufferBalance--;
        }
    }
}