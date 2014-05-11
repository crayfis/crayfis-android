package edu.uci.crayfis;

import edu.uci.crayfis.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class UserRegisterActivity extends PreferenceActivity {
	@Override
	 public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	 
	        addPreferencesFromResource(R.xml.register);
	 
	    }

}
