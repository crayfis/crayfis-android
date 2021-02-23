package io.crayfis.android.trigger.L1;

import java.util.HashMap;

import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor extends TriggerProcessor {

    public static final String KEY_TARGET_EPM = "target_epm";
    public static final String KEY_TRIGGER_LOCK = "trig_lock";
    public static final String KEY_L1_THRESH = "l1thresh";
    public static final String KEY_PRESCALE = "prescale";

    public static int L1CountData;

    private static L1Calibrator sCalibrator = new L1Calibrator(1,1);

    private L1Processor(CFApplication application, ExposureBlock xb, Config config) {
        super(application, xb, config, false);
    }

    public static TriggerProcessor makeProcessor(CFApplication application, ExposureBlock xb) {
        CFConfig config = CFConfig.getInstance();
        Config l1Config = config.getL1Trigger();
        boolean prescale = l1Config.getBoolean(KEY_PRESCALE);
        int nFrames = l1Config.getInt(TriggerProcessor.Config.KEY_MAXFRAMES);
        int nBins = DAQManager.getInstance().isStreamingRAW() ? 1024 : 256;

        // update L1Calibrator
        if(sCalibrator.nBins  != nBins) {
            sCalibrator = new L1Calibrator(nFrames, nBins);
            config.setThresholds(null);

            // this should never happen, but we can make sure anyway
            if(application.getApplicationState() != CFApplication.State.CALIBRATION)
                application.setApplicationState(CFApplication.State.CALIBRATION);

        } else if(sCalibrator.size() != nFrames) {
            sCalibrator.updateThresholds(prescale);
            sCalibrator.resize(nFrames); // resize after updating thresholds
        } else {
            sCalibrator.updateThresholds(prescale);
        }

        // now use updated trigger
        return new L1Processor(application, xb, CFConfig.getInstance().getL1Trigger());
    }

    public static Config makeConfig(String configStr) {

        HashMap<String, String> options = TriggerProcessor.parseConfigString(configStr);
        String name = options.get("name");
        options.remove("name");

        if(options.containsKey(L1Processor.KEY_TARGET_EPM)) {
            CFConfig cfg = CFConfig.getInstance();
            double targetFrames = 60 * cfg.getExposureBlockTargetEvents()
                    * cfg.getTargetFPS() / Float.parseFloat(options.get(L1Processor.KEY_TARGET_EPM));
            options.put(Config.KEY_MAXFRAMES, Integer.toString((int)(targetFrames)));
        }

        switch (name) {
            case L1Task.Config.NAME:
                return new L1Task.Config(options);
            default:
                CFLog.w("No L1 implementation found for " + name + ", using default!");
                return new L1Task.Config(options);
        }

    }

    @Override
    public void onMaxReached() {
        if(application.getApplicationState() == CFApplication.State.CALIBRATION) {
            sCalibrator.submitCalibrationResult(application);
            application.setApplicationState(CFApplication.State.DATA);
        }
    }

    public static L1Calibrator getCalibrator() {
        return sCalibrator;
    }
}
