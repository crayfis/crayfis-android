package edu.uci.crayfis;

import android.app.Application;

import com.crashlytics.android.Crashlytics;

/**
 * Extension of {@link android.app.Application}.
 */
public class CFApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Crashlytics.start(this);
    }
}
