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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;

import edu.uci.crayfis.usernotif.UserNotificationActivity;
import edu.uci.crayfis.util.CFLog;



/**
 * Simple activity used to start other activities
 *
 * @author Peter Abeles
 */
public class MainActivity extends Activity  {

	private static final int REQUEST_CODE_WELCOME = 1;
	private static final int REQUEST_CODE_HOW_TO = 2;
    public static final int WRITE_SETTINGS_ACTIVITY = 3;

    public static final String[] permissions = {
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    };

	public String build_version = null;

	static {
		if(OpenCVLoader.initDebug()) {
			CFLog.d("OpenCV installed");
		} else {
			CFLog.d("OpenCV not installed");
		}
	}

	@Override
	public void onRestart() {
        super.onRestart();
        CFLog.d("onRestart()");
	}

	@Override
    public void onStart() {
        super.onStart();
        CFLog.d("onStart()");
    }

    @Override
    public void onStop() {
        super.onStop();
        CFLog.d("onStop()");
    }

    @Override
    public void onPause() {
        super.onPause();
        CFLog.d("onPause()");
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, 0);
        } else {
            // no need to ask for permissions here
            startDAQ();
        }
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
        CFLog.d("onActivityResult()");
		if (resultCode != RESULT_OK) {
			//finish();
		} else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && requestCode == WRITE_SETTINGS_ACTIVITY) {
                CFLog.d("Write setttings activity");
                requestPermissions(permissions, 0);
            } else if (requestCode == REQUEST_CODE_WELCOME) {
                final Intent intent = new Intent(this, UserNotificationActivity.class);
                intent.putExtra(UserNotificationActivity.TITLE, "How To Use This App");
                intent.putExtra(UserNotificationActivity.MESSAGE, "Please plug your device into a power source and put it down with the rear camera facing down.\n\nPlugging in your device is not required but highly recommended.  This app uses a lot of power.\n\nMake sure your location services are turned on.  Providing us with your location allows us to make the most out of the data you collect.");
                startActivityForResult(intent, REQUEST_CODE_HOW_TO);
			} else {
				//This is so that if they have entered in an invalid user ID before, but
				//then just decide to run it locally, it will reset the userID to empty
				SharedPreferences sharedprefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				final SharedPreferences.Editor editor = sharedprefs.edit();
				editor.putBoolean("firstRun", false);
				editor.commit();

				// now start running
				Intent intent = new Intent(this, DAQActivity.class);
				startActivity(intent);
				finish();
			}
		}
	}

	@Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        CFLog.d("onRequestPermissionsResult()");

        boolean permissionError = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        boolean writeError = !Settings.System.canWrite(this);

        if(!(permissionError || writeError)) {
            startDAQ();
        } else {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            if (permissionError) {
                builder.setMessage(R.string.permission_error)
                        .setPositiveButton(R.string.permission_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                requestPermissions(MainActivity.permissions, 0);
                            }
                        })
                        .setNegativeButton(R.string.permission_no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        });

            } else {
                builder.setMessage(R.string.write_settings_error)
                        .setPositiveButton(R.string.permission_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                                intent.setData(Uri.parse("package:" +getPackageName()))
                                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                // FIXME: this doesn't work, and the user has to restart the app instead
                                startActivityForResult(intent, WRITE_SETTINGS_ACTIVITY);
                            }
                        })
                        .setNegativeButton(R.string.permission_no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        });
            }

            builder.setTitle(R.string.permission_error_title)
                    .setCancelable(false)
                    .show();
        }
    }

    private void startDAQ() {
        try {
            build_version = getPackageManager().getPackageInfo(getPackageName(),0).versionName;
        }
        catch (NameNotFoundException ex) {
            CFLog.w("MainActivity: Could not find build version!");
        }

        //Pull the existing shared preferences and set editor
        SharedPreferences sharedprefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean firstRun = sharedprefs.getBoolean("firstRun", true);
        if (firstRun) {
            final Intent intent = new Intent(this, UserNotificationActivity.class);
            intent.putExtra(UserNotificationActivity.TITLE, R.string.app_name);
            intent.putExtra(UserNotificationActivity.MESSAGE, R.string.userIDlogin1);
            startActivityForResult(intent, REQUEST_CODE_WELCOME);
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