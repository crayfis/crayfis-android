package io.crayfis.android.trigger.L1;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.R;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.trigger.calibration.L1Calibrator;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by cshimmin on 5/12/16.
 */
class L1Task extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {
        static final int DEFAULT_THRESH = 255;

        final int thresh;
        Config(String name, String cfg) {
            super(name, cfg);

            thresh = CFUtil.getInt(mTaskConfig.get("thresh"), DEFAULT_THRESH);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor l1Processor, RawCameraFrame frame) {
            return new L1Task(l1Processor, frame, this);
        }
    }

    private ExposureBlock mExposureBlock;
    private CFApplication mApplication;
    private L1Calibrator mL1Cal;
    private PreCalibrator mPrecal;
    private boolean mKeepFrame = false;
    private final Config mConfig;

    private final CFConfig CONFIG = CFConfig.getInstance();

    L1Task(TriggerProcessor processor, RawCameraFrame frame, Config cfg) {
        super(processor, frame);
        mExposureBlock = frame.getExposureBlock();

        mApplication = processor.mApplication;
        mL1Cal = L1Calibrator.getInstance();
        mPrecal = PreCalibrator.getInstance(mApplication);
        mConfig = cfg;
    }

    boolean processInitial(RawCameraFrame frame) {
        // check for quality data
        if(!frame.isQuality()) {
            CFCamera camera = CFCamera.getInstance();
            camera.changeCameraFrom(frame.getCameraId());
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

    boolean processPreCalibration(RawCameraFrame frame) {

        if(mPrecal.addFrame(frame)) {
            mApplication.setNewestPrecalUUID();
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        } else if(mPrecal.count.incrementAndGet()
                % (CONFIG.getTargetFPS()*CONFIG.getExposureBlockPeriod()) == 0) {
            mApplication.checkBatteryStats();
        }

        return false;
    }

    boolean processCalibration(RawCameraFrame frame) {
        // if we are in (L1) calibration mode, there's no need to do anything else with this
        // frame; the L1 calibrator already saw it. Just check to see if we're done calibrating.
        long count = mExposureBlock.count.incrementAndGet();
        mL1Cal.addFrame(frame);

        if (count == CONFIG.getCalibrationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.DATA);
        }

        return true;
    }

    boolean processStabilization(RawCameraFrame frame) {
        // If we're in stabilization mode, just drop frames until we've skipped enough
        long count = mExposureBlock.count.incrementAndGet();
        if (count == CONFIG.getStabilizationSampleFrames()) {
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
        return true;
    }

    boolean processIdle(RawCameraFrame frame) {
        // Not sure why we're still acquiring frames in IDLE mode...
        CFLog.w("DAQActivity Frames still being received in IDLE mode");
        return true;
    }

    boolean processData(RawCameraFrame frame) {

        mL1Cal.addFrame(frame);
        L1Processor.L1CountData++;

        int max = frame.getPixMax();

        mExposureBlock.underflow_hist.fill(frame.getHist());

        if (max > mConfig.thresh) {
            // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
            // have changed.

            mProcessor.pass++;
            
            // add a new buffer to the queue to make up for this one which
            // will not return
            if(frame.claim()) {
                // this frame has passed the L1 threshold, put it on the
                // L2 processing queue.
                mKeepFrame = true;
            } else {
                // out of memory: skip the frame
                mProcessor.mNextProcessor.skip++;
            }

        } else {
            // didn't pass. recycle the buffer.
            mProcessor.skip++;
        }

        return false;
    }

    void processFinal() {
    }


    @Override
    public boolean processFrame(RawCameraFrame frame) {

        L1Processor.L1Count++;

        if (processInitial(frame)) { return false; }

        boolean stopProcessing;
        switch (mExposureBlock.getDAQState()) {
            case PRECALIBRATION:
                stopProcessing = processPreCalibration(frame);
                break;
            case CALIBRATION:
                stopProcessing = processCalibration(frame);
                break;
            case STABILIZATION:
                stopProcessing = processStabilization(frame);
                break;
            case IDLE:
                stopProcessing = processIdle(frame);
                break;
            case DATA:
                stopProcessing = processData(frame);
                break;
            default:
                CFLog.w("Unimplemented state encountered in processFrame()! Dropping frame.");
                stopProcessing = true;
                break;
        }

        if (!stopProcessing) {
            processFinal();
        }

        if (!mKeepFrame) {
            // we are done with this frame. retire the buffer and also clear it from the XB.
            frame.retire();
        }

        if (frame.isOutstanding()) {
            CFLog.w("Frame still outstanding after running L1Task!");
        }
        
        return mKeepFrame;
    }
}