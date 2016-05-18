package edu.uci.crayfis.trigger;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/17/16.
 */
public abstract class L2Config {

    public static L2Config makeConfig(String trigstr) {
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
            default:
                CFLog.w("No L2 implementation found for " + name + ", using default!");
                cfg = new L2Task.Config(name, cfgstr);
                break;
        }

        return cfg;
    }


    public final String mName;
    public final String mConfig;

    protected L2Config(String name, String cfg) {
        mName = name;
        mConfig = cfg;

        parseConfig();
    }

    public abstract L2Task makeTask(L2Processor l2Processor, RawCameraFrame frame);

    protected void parseConfig() {
        return;
    }

    @Override
    public final String toString() {
        return mName + ";" + mConfig;
    }
}
