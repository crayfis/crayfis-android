package io.crayfis.android.ui.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import io.crayfis.android.BuildConfig;
import io.crayfis.android.R;

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
