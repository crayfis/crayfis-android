package edu.uci.crayfis;

import java.io.FileOutputStream;

import edu.uci.crayfis.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class UserRegisterActivity extends PreferenceActivity {
	@Override
	 public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	 
	        addPreferencesFromResource(R.xml.register);
	 
	    }
	

}
