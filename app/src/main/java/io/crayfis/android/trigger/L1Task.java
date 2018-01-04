package io.crayfis.android.trigger;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.crayfis.android.CFApplication;
import io.crayfis.android.R;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
class L1Task implements Runnable {
    public static class Config extends L1Config {
        Config(String name, String cfg) {
            super(name, cfg);
        }

        @Override
        public L1Task makeTask(L1Processor l1Processor, RawCameraFrame frame) {
            return new L1Task(l1Processor, frame);
        }
    }

    private L1Processor mL1Processor = null;
    private L2Processor mL2Processor = null;
    private RawCameraFrame mFrame = null;
    private ExposureBlock mExposureBlock = null;
    private CFApplication mApplication = null;
    private boolean mKeepFrame = false;

    private final CFConfig CONFIG = CFConfig.getInstance();

    public L1Task(L1Processor l1processor, RawCameraFrame frame) {
        mL1Processor = l1processor;
        mFrame = frame;
        mExposureBlock = mFrame.getExposureBlock();

        mApplication = mL1Processor.mApplication;
        mL2Processor = mL1Processor.mL2Processor;
    }

    protected boolean processInitial() {
        // check for quality data
        if(!mFrame.isQuality()) {
            CFCamera camera = CFCamera.getInstance();
            camera.changeCameraFrom(mFrame.getCameraId());
            if(!camera.isFlat()) {
                mApplication.userErrorMessage(R.string.warning_facedown, false);
            } else {
                camera.badFlatEvents++;
                if(camera.badFlatEvents < 5) {
                    mApplication.userErrorMessage(R.string.warning_bright, false);
                } else {
                    // gravity sensor is clearly impaired, so just determine orientation with light levels
                    mApplication.userErrorMessage(R.string.sensor_error, false);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApplication);
                    prefs.edit()
                            .putString("prefCameraSelectMode", "1")
                            .apply();
                }
            }
            return true;
        }
        return false;
    }

    protected boolean processPreCalibration() {

        if(mL1Processor.mPreCal.addFrame(mFrame)) {
            mApplication.setNewestPrecalUUID();
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        } else if(mL1Processor.mPreCal.count.incrementAndGet()
                % (CONFIG.getTargetFPS()*CONFIG.getExposureBlockPeriod()) == 0) {
            mApplication.checkBatteryStats();
        }

        return false;
    }

    protected boolean processCalibration() {
        // if we are in (L1) calibration mode, there's no need to do anything else with this
        // frame; the L1 calibrator already saw it. Just check to see if we're done calibrating.
        long count = mExposureBlock.count.incrementAndGet();
        mL1Processor.mL1Cal.addFrame(mFrame);

        if (count == mL1Processor.CONFIG.getCalibrationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.DATA);
        }

        return true;
    }

    protected boolean processStabilization() {
        // If we're in stabilization mode, just drop frames until we've skipped enough
        long count = mExposureBlock.count.incrementAndGet();
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

        mL1Processor.mL1Cal.addFrame(mFrame);
        mL1Processor.mL1CountData++;

        int max = mFrame.getPixMax();

        mExposureBlock.underflow_hist.fill(mFrame.getHist());

        if (max > mExposureBlock.getL1Thresh()) {
            // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
            // have changed.

            mExposureBlock.L1_pass++;



            // add a new buffer to the queue to make up for this one which
            // will not return
            if(mFrame.claim()) {
                // this frame has passed the L1 threshold, put it on the
                // L2 processing queue.
                mL2Processor.submitFrame(mFrame);
                mKeepFrame = true;
            } else {
                // out of memory: skip the frame
                mExposureBlock.L2_skip++;
            }

        } else {
            // didn't pass. recycle the buffer.
            mExposureBlock.L1_skip++;
        }

        return false;
    }

    protected void processFinal() {
    }

    protected void processFrame() {

        if (processInitial()) { return; }

        boolean stopProcessing;
        switch (mExposureBlock.getDAQState()) {
            case PRECALIBRATION:
                stopProcessing = processPreCalibration();
                break;
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

        if (!mKeepFrame) {
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