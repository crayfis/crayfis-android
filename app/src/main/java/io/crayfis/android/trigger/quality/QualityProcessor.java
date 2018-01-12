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

    public QualityProcessor(CFApplication application, String configStr) {
        super(application, configStr, false);
    }

    @Override
    public Config makeConfig(String name, HashMap<String, String> options) {
        switch (name) {
            case "facedown":
                return new QualityTaskFacedown.Config(name, options);
            case "autodetect":
                return new QualityTaskAutodetect.Config(name, options);
            case "lock":
                return new QualityTaskLock.Config(name, options);
            default:
                CFLog.w("No L0 implementation found for " + name + ", using default!");
                return new QualityTaskFacedown.Config(name, options);
        }
    }

    @Override
    public void processResult(RawCameraFrame frame, boolean pass) {
        if(!pass) {
            CFCamera camera = CFCamera.getInstance();
            camera.changeCameraFrom(frame.getCameraId());
            if(!camera.isFlat()) {
                mApplication.userErrorMessage(R.string.warning_facedown, false);
            } else {
                camera.badFlatEvents++;
                if(camera.badFlatEvents < 5) {
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



}
