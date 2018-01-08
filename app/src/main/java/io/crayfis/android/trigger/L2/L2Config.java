package io.crayfis.android.trigger.L2;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/17/16.
 */
abstract class L2Config {

    static L2Config makeConfig(String trigstr) {
        String[] pieces = trigstr.split(";", 2);

        String name = pieces[0];
        String cfgstr = pieces.length==2 ? pieces[1] : "";

        L2Config cfg;
        switch (name) {
            case "default":
                cfg = new L2Task.Config(name, cfgstr);
                break;
            case "maxn":
                cfg = new L2TaskMaxN.Config(name, cfgstr);
                break;
            case "byteblock":
                cfg = new L2TaskByteBlock.Config(name, cfgstr);
                break;
            default:
                CFLog.w("No L2 implementation found for " + name + ", using default!");
                cfg = new L2Task.Config(name, cfgstr);
                break;
        }

        return cfg;
    }


    private final String mName;
    private final String mConfig;

    L2Config(String name, String cfg) {
        mName = name;
        mConfig = cfg;

        parseConfig();
    }

    public abstract L2Task makeTask(L2Processor l2Processor, RawCameraFrame frame);

    void parseConfig() {
        return;
    }

    @Override
    public final String toString() {
        return mName + ";" + mConfig;
    }
}
