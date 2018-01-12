package io.crayfis.android.trigger.L1;

import java.util.HashMap;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/4/16.
 */

public class L1Processor extends TriggerProcessor {

    public static int L1Count;
    public static int L1CountData;

    public L1Processor(CFApplication application, String configStr) {
        super(application, configStr, false);
    }

    @Override
    public Config makeConfig(String name, HashMap<String, String> options) {

        switch (name) {
            case "default":
                return new L1Task.Config(name, options);
            default:
                CFLog.w("No L1 implementation found for " + name + ", using default!");
                return new L1Task.Config(name, options);
        }

    }
}
