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

package io.crayfis.android;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.opencv.android.OpenCVLoader;

import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.usernotif.UserNotificationActivity;
import io.crayfis.android.util.CFLog;



/**
 * Simple activity used to start other activities
 *
 * @author Peter Abeles
 */
public class MainActivity extends Activity  {

    private static final int REQUEST_CODE_PERMISSIONS = 0;

	private static final int REQUEST_CODE_WELCOME = 1;
	private static final int REQUEST_CODE_HOW_TO = 2;
    private static final int REQUEST_CODE_AUTOSTART = 3;

    public static String[] permissions = {
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

        boolean firstRun = sharedprefs.getBoolean("firstRun", true);
        if (firstRun) {
            final Intent intent = new Intent(this, UserNotificationActivity.class);
            intent.putExtra(UserNotificationActivity.TITLE, R.string.first_run_welcome_title);
            intent.putExtra(UserNotificationActivity.MESSAGE, R.string.first_run_welcome);
            startActivityForResult(intent, REQUEST_CODE_WELCOME);
        } else {
            checkPermissions();
        }
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode != RESULT_OK) {
            finish();
		} else {
            Intent intent;
            switch(requestCode) {
                case REQUEST_CODE_WELCOME:
                    intent = new Intent(this, UserNotificationActivity.class);
                    intent.putExtra(UserNotificationActivity.TITLE, R.string.first_run_how_to_title);
                    intent.putExtra(UserNotificationActivity.MESSAGE, R.string.first_run_how_to);
                    startActivityForResult(intent, REQUEST_CODE_HOW_TO);
                    break;
                case REQUEST_CODE_HOW_TO:
                    intent = new Intent(this, ConfigureAutostartActivity.class);
                    startActivityForResult(intent, REQUEST_CODE_AUTOSTART);
                    break;
                case REQUEST_CODE_AUTOSTART:
                    //This is so that if they have entered in an invalid user ID before, but
                    //then just decide to run it locally, it will reset the userID to empty
                    SharedPreferences sharedprefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    final SharedPreferences.Editor editor = sharedprefs.edit();
                    editor.putBoolean("firstRun", false)
                            .apply();

                    // now start running
                    checkPermissions();
            }
		}
	}

    /**
     *  Request relevant permissions if not already enabled
     */
    private void checkPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermissions(this)) {
            requestPermissions(permissions, REQUEST_CODE_PERMISSIONS);
        } else {
            // ready to start DAQActivity
            Intent intent = new Intent(this, DAQActivity.class);
            startActivity(intent);
            finish();
        }
    }

	@Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        // loop through requested permissions and see if any were denied
        for(String permission: permissions) {
            if(checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                // notify the user that we need permissions and try again
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setTitle(R.string.permission_error_title)
                        .setCancelable(false)
                        .setPositiveButton(R.string.permission_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                requestPermissions(MainActivity.permissions, REQUEST_CODE_PERMISSIONS);
                            }
                        });

                // see if we absolutely need the permission
                if(!permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) || UploadExposureService.IS_PUBLIC) {

                    builder.setMessage(R.string.permission_error)
                            .setNegativeButton(R.string.permission_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    });
                } else {
                    // if it's a problem, we can shut off the gallery
                    builder.setMessage(R.string.gallery_dcim_error)
                            .setNegativeButton(getResources().getString(R.string.permission_no), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
                                            .edit()
                                            .putBoolean(getString(R.string.prefEnableGallery), false)
                                            .apply();
                                    startActivity(new Intent(MainActivity.this, DAQActivity.class));
                                    finish();
                                }
                            });
                }

                builder.show();
                return;
            }
        }

        // if all permissions are granted, we start data-taking
        startActivity(new Intent(this, DAQActivity.class));
        finish();

    }

    /**
     * Checks whether CRAYFIS can run given the currently allotted permissions
     *
     * @return true if all appropriate permissions have been granted
     */
    public static boolean hasPermissions(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if(UploadExposureService.IS_PUBLIC
                || prefs.getBoolean(context.getString(R.string.prefEnableGallery), false)) {
            permissions = new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String p : permissions) {
                if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

}