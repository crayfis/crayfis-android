package io.crayfis.android;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.List;

public class UserSettingsFragment extends PreferenceFragment {
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        // extra options for developers
        if(BuildConfig.DEBUG) {
            addPreferencesFromResource(R.xml.settings_developer);
        }

    }


}
