package io.crayfis.android.trigger.L0;

import java.util.HashMap;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

public class L0Processor extends TriggerProcessor {

    public static int L0Count;

    public L0Processor(CFApplication application, String configString) {
        super(application, configString, true);
    }

    @Override
    public Config makeConfig(String name, HashMap<String, String> options) {

        switch (name) {
            case "default":
                return new L0Task.Config(name, options);
            default:
                CFLog.w("No L0 implementation found for " + name + ", using default!");
                return new L0Task.Config(name, options);
        }

    }
}
