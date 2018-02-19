package io.crayfis.android.trigger.quality;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashMap;

import io.crayfis.android.R;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 1/11/18.
 */

public class QualityProcessor extends TriggerProcessor {

    static final String KEY_MEAN_THRESH = "mean";
    static final String KEY_ST_DEV_THRESH = "std";
    static final String KEY_ORIENT_THRESH = "orient";
    static final String KEY_BACKLOCK = "back";

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
            CFCamera camera = CFCamera.getInstance();
            camera.changeCameraFrom(frame.getCameraId());
            if (!camera.isFlat()) {
                mApplication.userErrorMessage(R.string.warning_facedown, false);
            } else {
                camera.badFlatEvents++;
                if (camera.badFlatEvents < 5) {
                    mApplication.userErrorMessage(R.string.warning_bright, false);
                } else {
                    // gravity sensor is clearly impaired, so just determine orientation with light levels
                    mApplication.userErrorMessage(R.string.sensor_error, false);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApplication);
                    prefs.edit()
                            .putString("prefCameraSelectMode", "1")
                            .apply();
                }
            }
        }

    }

    @Override
    public void onMaxReached() {
        // we have a sufficient number of good frames, so switch to CALIBRATION from SURVEY
        if(mApplication.getApplicationState() == CFApplication.State.SURVEY) {
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
    }

}
