package edu.uci.crayfis.trigger;

import edu.uci.crayfis.camera.frame.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/17/16.
 */
public abstract class L1Config {

    public static L1Config makeConfig(String trigstr) {
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


    public final String mName;
    public final String mConfig;

    protected L1Config(String name, String cfg) {
        mName = name;
        mConfig = cfg;

        parseConfig();
    }

    public abstract L1Task makeTask(L1Processor l1Processor, RawCameraFrame frame);

    protected void parseConfig() {
        return;
    }

    @Override
    public final String toString() {
        return mName + ";" + mConfig;
    }
}
