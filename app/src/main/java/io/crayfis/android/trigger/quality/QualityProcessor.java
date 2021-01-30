package io.crayfis.android.trigger.quality;

import java.util.HashMap;

import io.crayfis.android.R;
import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 1/11/18.
 */

public class QualityProcessor extends TriggerProcessor {

    public static final String KEY_MEAN_THRESH = "mean";
    public static final String KEY_ST_DEV_THRESH = "std";
    public static final String KEY_ORIENT_THRESH = "orient";
    public static final String KEY_BACKLOCK = "back";

    private QualityProcessor(CFApplication application, Config config) {
        super(application, config, false);
    }

    public static TriggerProcessor makeProcessor(CFApplication application) {
        return new QualityProcessor(application, CFConfig.getInstance().getQualTrigger());
    }

    public static Config makeConfig(String configStr) {

        HashMap<String, String> options = TriggerProcessor.parseConfigString(configStr);
        String name = options.get("name");
        options.remove("name");

        switch (name) {
            case QualityTaskFacedown.Config.NAME:
                return new QualityTaskFacedown.Config(options);
            case QualityTaskAutodetect.Config.NAME:
                return new QualityTaskAutodetect.Config(options);
            case QualityTaskLock.Config.NAME:
                return new QualityTaskLock.Config(options);
            default:
                CFLog.w("No Quality implementation found for " + name + ", using default!");
                return new QualityTaskFacedown.Config(options);
        }
    }

    @Override
    public void onFrameResult(RawCameraFrame frame, boolean pass) {
        if(!pass) {
            DAQManager daq = DAQManager.getInstance();
            daq.changeCameraFrom(frame.getCameraId());
            CFLog.d("Flat: " + daq.isPhoneFlat());
            if (!daq.isPhoneFlat()) {
                mApplication.userErrorMessage(false, R.string.warning_facedown);
            } else {
                mApplication.userErrorMessage(false, R.string.warning_bright);
            }
        }

    }

    @Override
    public void onMaxReached() {
        // we have a sufficient number of good frames, so switch to CALIBRATION from SURVEY
        if(mApplication.getApplicationState() == CFApplication.State.SURVEY) {
            mApplication.setApplicationState(CFApplication.State.PRECALIBRATION);
        }
    }

}
