package io.crayfis.android.trigger.L1;

import java.util.UUID;

import io.crayfis.android.DataProtos;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.FrameHistogram;
import io.crayfis.android.util.Histogram;
import io.crayfis.android.util.CFLog;

public class L1Calibrator {

    private final CFApplication mApplication;
    private static String sTaskName;
    private static final FrameHistogram sFrameStatistics = new FrameHistogram(1000);

    L1Calibrator(CFApplication application, TriggerProcessor.Config config) {
        mApplication = application;
        if(!config.getName().equals(sTaskName)
                || config.getInt(TriggerProcessor.Config.KEY_MAXFRAMES) != sFrameStatistics.size()) {
            synchronized (sFrameStatistics) {
                sTaskName = config.getName();
                sFrameStatistics.resize(config.getInt(TriggerProcessor.Config.KEY_MAXFRAMES));
            }
            if(mApplication.getApplicationState() != CFApplication.State.CALIBRATION) {
                mApplication.setApplicationState(CFApplication.State.CALIBRATION);
            }
        }
    }

    public static Integer[] getFrameStatistics() { return sFrameStatistics.toArray(new Integer[sFrameStatistics.size()]); }

    static void addStatistic(int stat) {
        synchronized (sFrameStatistics) {
            sFrameStatistics.addValue(stat);
        }
    }

    public static Histogram getHistogram() {
        return sFrameStatistics.getHistogram();
    }

    /**
     *  Find an integer L1 threshold s.t. the average L1 rate is less than
     *  or equal to the specified value and write to CFConfig
     */
    static void updateThresholds() {

        // first, find the target L1 efficiency
        TriggerProcessor.Config L1Config = CFConfig.getInstance().getL1Trigger();
        if(L1Config.getBoolean(L1Processor.KEY_TRIGGER_LOCK)) return;
        double fps = CFCamera.getInstance().getFPS();

        if (fps == 0) {
            CFLog.w("Warning! Got 0 fps in threshold calculation.");
        }
        double targetL1Rate = L1Config.getInt(L1Processor.KEY_TARGET_EPM) / 60.0 / fps;

        Histogram h = sFrameStatistics.getHistogram();
        long[] histValues = h.getValues();
        long nTotal = h.getEntries();
        int nTarget = (int) (nTotal * targetL1Rate);

        int thresh;

        for (thresh = 0; thresh < 255; thresh++) {
            nTotal -= histValues[thresh];
            //if (thresh<20) CFLog.d(" L1Calibrator. Thresh="+thresh+" integral="+h.getIntegral(thresh, 256)+" rate="+rate+" compare to "+target_eff);
            if (nTotal < nTarget) {
                break;
            }
        }

        CFLog.i("Setting new L1 threshold: {" + L1Config.getInt(L1Processor.KEY_L1_THRESH) + "} -> {" + thresh + "}");

        CFConfig CONFIG = CFConfig.getInstance();
        CONFIG.setL1Threshold(thresh);
        if (thresh > 2) {
            CONFIG.setL2Threshold(thresh - 1);
        } else {
            // Okay, if we're getting this low, we shouldn't try to
            // set the L2thresh any lower, else event frames will be huge.
            CONFIG.setL2Threshold(thresh);
        }
    }

    void submitCalibrationResult() {
        // build the calibration result object
        UUID runId = mApplication.getBuildInformation().getRunId();

        DataProtos.CalibrationResult.Builder cal = DataProtos.CalibrationResult.newBuilder()
                .setRunId(runId.getLeastSignificantBits())
                .setRunIdHi(runId.getMostSignificantBits())
                .setEndTime(System.currentTimeMillis());

        for (long v : sFrameStatistics.getHistogram()) {
            cal.addHistMaxpixel((int)v);
        }

        // and commit it to the output stream
        CFLog.i("DAQService Committing new calibration result.");
        UploadExposureService.submitCalibrationResult(mApplication, cal.build());
    }

}
