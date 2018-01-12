package io.crayfis.android.trigger.L1;

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
    public Config makeConfig(String configStr) {

        String[] pieces = configStr.split(";", 2);

        String name = pieces[0];
        String cfgstr = pieces.length==2 ? pieces[1] : "";

        Config cfg;
        switch (name) {
            case "default":
                cfg = new L1Task.Config(name, cfgstr);
                break;
            default:
                CFLog.w("No L1 implementation found for " + name + ", using default!");
                cfg = new L1Task.Config(name, cfgstr);
                break;
        }

        return cfg;
    }
}
