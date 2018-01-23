package io.crayfis.android.trigger.L1;

import java.util.HashMap;

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

    public static int L1CountData;
    private final L1Calibrator mL1Cal;

    private L1Processor(CFApplication application, Config config) {
        super(application, config, false);
        mL1Cal = new L1Calibrator(application, config);
    }

    public static TriggerProcessor makeProcessor(CFApplication application) {
        L1Calibrator.updateThresholds();
        return new L1Processor(application, CFConfig.getInstance().getL1Trigger());
    }

    public static Config makeConfig(String configStr) {

        HashMap<String, String> options = TriggerProcessor.parseConfigString(configStr);
        String name = options.get("name");
        options.remove("name");

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
        if(mApplication.getApplicationState() == CFApplication.State.CALIBRATION) {
            mL1Cal.submitCalibrationResult();
            mApplication.setApplicationState(CFApplication.State.DATA);
        }
    }
}
