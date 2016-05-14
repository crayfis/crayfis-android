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
    private Camera mCamera = null;
    private boolean mFrameOutstanding = true;
    private boolean mRecycle = true;
    private CFApplication mApplication = null;

    public L1Task(L1Processor l1processor, RawCameraFrame frame, Camera camera) {
        mL1Processor = l1processor;
        mFrame = frame;
        mCamera = camera;

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
        int count = ++mL1Processor.mCalibrationCount;

        if (count > mL1Processor.CONFIG.getCalibrationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.DATA);
        }

        mFrame.retire();
        return true;
    }

    protected boolean processStabilization() {
        // If we're in stabilization mode, just drop frames until we've skipped enough
        int count = ++mL1Processor.mStabilizationCount;
        if (count > mL1Processor.CONFIG.getStabilizationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
        mFrame.retire();
        return true;
    }

    protected boolean processIdle() {
        // Not sure why we're still acquiring frames in IDLE mode...
        CFLog.w("DAQActivity Frames still being recieved in IDLE mode");
        mFrame.retire();
        return true;
    }

    protected boolean processData() {
        // check if we pass the L1 threshold
        boolean pass = false;

        ExposureBlock xb = mFrame.getExposureBlock();

        int max = mFrame.getPixMax();

        xb.total_background += mFrame.getPixAvg();
        xb.total_max += mFrame.getPixMax();

        if (max > xb.L1_thresh) {
            // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
            // have changed.
            pass = true;
        }

        if (pass) {
            xb.L1_pass++;

            // add a new buffer to the queue to make up for this one which
            // will not return
            // TODO: make sure we check that there's enough memory to allocate a frame!
            mFrame.claim();

            // this frame has passed the L1 threshold, put it on the
            // L2 processing queue.
            mL2Processor.submitFrame(mFrame);
        } else {
            // didn't pass. recycle the buffer.
            xb.L1_skip++;
            mFrame.retire();
        }

        return false;
    }

    protected void processFinal() {
        return;
    }

    private void processFrame() {
        if (processInitial()) { return; }

        boolean stopProcessing;
        switch (mApplication.getApplicationState()) {
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
                mFrame.retire();
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

        if (mFrame.isOutstanding()) {
            CFLog.w("Frame still outstanding after running L1Task!");
        } else {
            mL1Processor.mBufferBalance--;
        }
    }
}