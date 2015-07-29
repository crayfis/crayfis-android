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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import edu.uci.crayfis.util.CFLog;



/**
 * Simple activity used to start other activities
 *
 * @author Peter Abeles
 */
public class MainActivity extends Activity  {
	
	public String build_version = null;
	public String userID = null;

	
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
			CFLog.w("MainActivity: Could not find build version!");
		}

		//Pull the existing shared preferences and set editor
		SharedPreferences sharedprefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		final Editor editor = sharedprefs.edit();
		//Check if userID already inputted, and if not, go to sign in page
		String ID = sharedprefs.getString("prefUserID", "");
		boolean badID = sharedprefs.getBoolean("badID", false);
		boolean firstRun = sharedprefs.getBoolean("firstRun", true);
		if (firstRun) {
			editor.putBoolean("firstRun", false);
			editor.apply();
		}

		if (firstRun) {
			setContentView(R.layout.main);

            TextView whattodo = (TextView)findViewById(R.id.what_to_do);
            whattodo.setMovementMethod(new ScrollingMovementMethod());

            final Button button2 = (Button)findViewById(R.id.run_without_user_id);
			button2.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					//This is so that if they have entered in an invalid user ID before, but
					//then just decide to run it locally, it will reset the userID to empty
					editor.putString("prefUserID", "");
					
					// now start running
					Intent intent = new Intent(MainActivity.this, DAQActivity.class);
					startActivity(intent);

					// now quit
					MainActivity.this.finish();					
				}
			});
		}
		else {
		//See if we already have user ID saved
		//Because then no need to login again
		//if((ID != "") && !badID) {
			Intent intent = new Intent(MainActivity.this, DAQActivity.class);
			startActivity(intent);
			//and quit
			MainActivity.this.finish();
		}
	}
    
}