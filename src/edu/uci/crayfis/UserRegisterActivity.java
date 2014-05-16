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
	
	public void onDestroy()
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		Boolean b = sharedPrefs.getBoolean("prefAnon", false);
		String anon = Boolean.toString(b);
		String uname = sharedPrefs.getString("prefUserName", "Default Name");
		String umail = sharedPrefs.getString("prefUserEmail", "DefaultMail");
		// the file output stream
		String cfile="crayfis_reg.txt";
		try {
		FileOutputStream fos = getApplicationContext().openFileOutput(cfile,android.content.Context.MODE_WORLD_READABLE);
		fos.write(uname.getBytes());
		fos.write(umail.getBytes());
		fos.write(anon.getBytes());
		} catch (Exception e) { }
		super.onDestroy();
	}

}
