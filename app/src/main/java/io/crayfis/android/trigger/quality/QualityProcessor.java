package io.crayfis.android.trigger.quality;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashMap;

import io.crayfis.android.R;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 1/11/18.
 */

public class QualityProcessor extends TriggerProcessor {

    public QualityProcessor(CFApplication application, Config config) {
        super(application, config, false);
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
                CFLog.w("No L0 implementation found for " + name + ", using default!");
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
        // we have a sufficient number of good frames, so switch to CALIBRATION from STABILIZATION
        if(mApplication.getApplicationState() == CFApplication.State.STABILIZATION) {
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
    }

}
