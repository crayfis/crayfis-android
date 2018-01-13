package io.crayfis.android.trigger.L0;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

public class L0Processor extends TriggerProcessor {

    static AtomicInteger L0Count;

    public L0Processor(CFApplication application, Config config) {
        super(application, config, true);
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
