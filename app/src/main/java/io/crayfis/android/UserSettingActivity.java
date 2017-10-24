package io.crayfis.android;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import java.util.List;

public class UserSettingActivity extends PreferenceActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new UserSettingsFragment())
                .commit();
	 
	}


}
