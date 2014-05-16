/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.crayfis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import edu.uci.crayfis.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;



/**
 * Simple activity used to start other activities
 *
 * @author Peter Abeles
 */
public class MainActivity extends Activity  {
	
	private static final int RESULT_SETTINGS = 1;
	private static final int RESULT_REGISTER = 2;
	
	public String build_version = null;

	
	public void onRestart()
	{
		super.onRestart();
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			build_version = getPackageManager().getPackageInfo(getPackageName(),0).versionName;
		}
		catch (NameNotFoundException ex) {
			Log.w("MainActivity", "Could not find build version!");
		}
		
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().clear().commit();
		PreferenceManager.setDefaultValues(this, R.xml.settings, true);
		PreferenceManager.setDefaultValues(this, R.xml.register, true);		
		
		// look for previous registration information
		File sdcard = Environment.getExternalStorageDirectory();
		File file = new File(sdcard,"crayfis_reg.txt");
		Log.d("MainActivity","Does file exist? "+file.exists());
		// check if file exists
		if (file.exists())
		{
						
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

			//Read text from file
			try {
		    BufferedReader br = new BufferedReader(new FileReader(file));
		    String firstName= br.readLine();
		    String lastName = br.readLine();
		    String email    = br.readLine();
		    boolean anon = Boolean.parseBoolean(br.readLine());

		    // write to prefs
		    Editor editor = sharedPrefs.edit();
		    editor.putString("prefUserName",firstName+" "+lastName);
		    editor.putString("prefUserEmail",email);
		    editor.putBoolean("prefAnon",anon);
		    editor.commit();
			}
			catch (IOException e) 
			{
			    //You'll need to add proper error handling here
			}
		}
			
		// now start running
		Intent intent = new Intent(this, DAQActivity.class);
		startActivity(intent);
		
		// now quit
		MainActivity.this.finish();
	}

	public void clickedVideo( View view ) {
		Intent intent = new Intent(this, DAQActivity.class);
		startActivity(intent);
	}
	
	
 
    public void clickedSettings(View view) {

            Intent i = new Intent(this, UserSettingActivity.class);
            startActivityForResult(i, RESULT_SETTINGS); 
    }
 
    public void clickedRegister(View view) {

        Intent i = new Intent(this, UserRegisterActivity.class);
        startActivityForResult(i, RESULT_REGISTER); 
}
 
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
 
        switch (requestCode) {
        case RESULT_SETTINGS:
            showUserSettings();
            break;       
        case RESULT_REGISTER:
        	showUserSettings();
        	break;

    } 
    }
    
	 private void showUserSettings() {
	        SharedPreferences sharedPrefs = PreferenceManager
	                .getDefaultSharedPreferences(this);
	 
	        StringBuilder builder = new StringBuilder();
	 
	        builder.append("\n Upload data? "
	                + sharedPrefs.getBoolean("prefUploadData", false));
	        builder.append("\n Do Reco? "
	                + sharedPrefs.getBoolean("prefDoReco", false));
	        builder.append("\n Threshold: "
	                + sharedPrefs.getString("prefThreshold", "NULL"));
	        builder.append("\n Run Name: "+ sharedPrefs.getString("prefRunName","NULL"));
	        
	        builder.append("\n Your Name: "+ sharedPrefs.getString("prefUserName","NULL"));
	        builder.append("\n Your email: "+ sharedPrefs.getString("prefUserEmail","NULL"));
	        builder.append("\n Anonymous?: "
	                + sharedPrefs.getBoolean("prefAnon", false));
	        builder.append("\n Build version: " + build_version);
	        TextView settingsTextView = (TextView) findViewById(R.id.text_settings);
	
	       
	        settingsTextView.setTextSize(15);
	       
	        settingsTextView.setText(builder.toString());
	    }
	 
}