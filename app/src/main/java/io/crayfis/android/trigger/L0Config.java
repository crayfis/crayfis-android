package io.crayfis.android.trigger;

import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 1/4/18.
 */

public abstract class L0Config {
    public static L0Config makeConfig(String trigstr) {
        String[] pieces = trigstr.split(";", 2);

        String name = pieces[0];
        String cfgstr = pieces.length==2 ? pieces[1] : "";

        L0Config cfg;
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


    public final String mName;
    public final String mConfig;

    protected L0Config(String name, String cfg) {
        mName = name;
        mConfig = cfg;

        parseConfig();
    }

    public abstract L0Task makeTask(L0Processor l0Processor, RawCameraFrame frame);

    protected void parseConfig() {
        return;
    }

    @Override
    public final String toString() {
        return mName + ";" + mConfig;
    }
}
