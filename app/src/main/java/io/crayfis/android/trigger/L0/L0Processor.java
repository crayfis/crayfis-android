package io.crayfis.android.trigger.L0;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

public class L0Processor extends TriggerProcessor {

    public static final String KEY_PRESCALE = "prescale";
    public static final String KEY_RANDOM = "random";
    public static final String KEY_WINDOWSIZE = "windowsize";

    public static AtomicInteger L0Count = new AtomicInteger();

    private L0Processor(CFApplication application, ExposureBlock xb, Config config) {
        super(application, xb, config, true);
    }

    public static TriggerProcessor makeProcessor(CFApplication application, ExposureBlock xb) {
        return new L0Processor(application, xb, CFConfig.getInstance().getL0Trigger());
    }

    public static Config makeConfig(String configStr) {

        HashMap<String, String> options = TriggerProcessor.parseConfigString(configStr);
        String name = options.get("name");
        options.remove("name");

        switch (name) {
            case L0Task.Config.NAME:
                return new L0Task.Config(options);
            default:
                CFLog.w("No L0 implementation found for " + name + ", using default!");
                return new L0Task.Config(options);
        }

    }
}
