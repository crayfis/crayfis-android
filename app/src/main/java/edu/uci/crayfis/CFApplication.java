package edu.uci.crayfis;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.crashlytics.android.Crashlytics;

/**
 * Extension of {@link android.app.Application}.
 */
public class CFApplication extends Application {

    private static final String SHARED_PREFS_NAME = "global";

    @Override
    public void onCreate() {
        super.onCreate();

        Crashlytics.start(this);

        SharedPreferences localPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        final CFConfig config = CFConfig.getInstance();
        localPrefs.registerOnSharedPreferenceChangeListener(config);
        // FIXME This is not needed when dependency injection is done, this can be handled in the constructor.
        config.onSharedPreferenceChanged(localPrefs, null);
    }
}
