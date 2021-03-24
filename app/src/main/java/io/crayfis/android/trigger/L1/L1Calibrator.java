package io.crayfis.android.trigger.L1;

import java.util.UUID;

import io.crayfis.android.DataProtos;
import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.FrameHistogram;
import io.crayfis.android.util.Histogram;
import io.crayfis.android.util.CFLog;

public class L1Calibrator extends FrameHistogram {

    L1Calibrator(int nFrames, int nBins) {
        super(nFrames, nBins);
    }

    public Integer[] getFrameStatistics() { return this.toArray(new Integer[this.size()]); }

    /**
     *  Find an integer L1 threshold s.t. the average L1 rate is less than
     *  or equal to the specified value and write to CFConfig
     */
    void updateThresholds(boolean prescale) {

        // if we have a trigger lock, just set the thresholds
        TriggerProcessor.Config L1Config = CFConfig.getInstance().getL1Trigger();
        if(L1Config.getBoolean(L1Processor.KEY_TRIGGER_LOCK)) {
            CFConfig.getInstance().setThresholds();
            return;
        }

        // first, find the target L1 efficiency
        double fps = DAQManager.getInstance().getFPS();

        if (fps == 0) {
            CFLog.w("Warning! Got 0 fps in threshold calculation.");
        }
        double targetL1Rate = L1Config.getFloat(L1Processor.KEY_TARGET_EPM) / 60.0 / fps;

        // convert this into a threshold
        Histogram h = getHistogram();
        long[] histValues = h.getValues();
        long nTotal = h.getEntries();
        int nTarget = (int) (nTotal * targetL1Rate); // note: this is 0 when the calibrator is empty

        int threshBase;
        double threshPrescale = 0;

        for (threshBase = 0; threshBase < histValues.length-1; threshBase++) {
            nTotal -= histValues[threshBase];
            if (nTotal < nTarget) {
                // take as much of the bin as necessary to give the desired rate
                threshPrescale = 1 - (double) (nTarget - nTotal) / histValues[threshBase--];
                break;
            }
        }

        double thresh = threshBase + threshPrescale;
        if(!prescale || thresh < 3) thresh = Math.ceil(thresh);

        CFLog.i("Setting new L1 threshold: {" + L1Config.getFloat(L1Processor.KEY_L1_THRESH) + "} -> {" + thresh + "}");

        CFConfig.getInstance().setThresholds((float) thresh);
    }

    void submitCalibrationResult(CFApplication application) {
        // build the calibration result object
        UUID runId = application.getBuildInformation().getRunId();

        DataProtos.CalibrationResult.Builder cal = DataProtos.CalibrationResult.newBuilder()
                .setRunId(runId.getLeastSignificantBits())
                .setRunIdHi(runId.getMostSignificantBits())
                .setEndTime(System.currentTimeMillis());

        for (long v : getHistogram()) {
            cal.addHistMaxpixel((int)v);
        }

        // and commit it to the output stream
        CFLog.i("DAQService Committing new calibration result.");
        UploadExposureService.submitMessage(application, DAQManager.getInstance().getCameraId(), cal.build());
    }

}
