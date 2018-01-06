package io.crayfis.android.trigger.L1;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/17/16.
 */
abstract class L1Config {

    static L1Config makeConfig(String trigstr) {
        String[] pieces = trigstr.split(";", 2);

        String name = pieces[0];
        String cfgstr = pieces.length==2 ? pieces[1] : "";

        L1Config cfg;
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


    private final String mName;
    private final String mConfig;

    L1Config(String name, String cfg) {
        mName = name;
        mConfig = cfg;

        parseConfig();
    }

    public abstract L1Task makeTask(L1Processor l1Processor, RawCameraFrame frame);

    void parseConfig() {
        return;
    }

    @Override
    public final String toString() {
        return mName + ";" + mConfig;
    }
}
