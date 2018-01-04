package io.crayfis.android.ui;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class UserSettingActivity extends PreferenceActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new UserSettingsFragment())
                .commit();
	 
	}


}
