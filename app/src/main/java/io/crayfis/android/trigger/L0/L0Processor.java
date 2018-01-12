package io.crayfis.android.trigger.L0;

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
    public Config makeConfig(String configStr) {
        String[] pieces = configStr.split(";", 2);

        String name = pieces[0];
        String cfgstr = pieces.length==2 ? pieces[1] : "";

        Config cfg;
        switch (name) {
            case "default":
                cfg = new L0Task.Config(name, cfgstr);
                break;
            default:
                CFLog.w("No L0 implementation found for " + name + ", using default!");
                cfg = new L0Task.Config(name, cfgstr);
                break;
        }

        return cfg;
    }
}
