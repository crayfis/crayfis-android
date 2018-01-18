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

    public static int L1CountData;

    private L1Processor(CFApplication application, Config config) {
        super(application, config, false);
    }

    public static TriggerProcessor makeProcessor(CFApplication application) {
        L1Calibrator.getInstance().updateThresholds();
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
            mApplication.setApplicationState(CFApplication.State.DATA);
        }
    }
}
