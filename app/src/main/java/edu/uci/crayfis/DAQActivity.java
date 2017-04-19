/* Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.navdrawer.NavDrawerAdapter;
import edu.uci.crayfis.navdrawer.NavHelper;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.server.UploadExposureTask;
import edu.uci.crayfis.trigger.L2Task;
import edu.uci.crayfis.ui.DataCollectionFragment;
import edu.uci.crayfis.ui.LayoutBlack;
import edu.uci.crayfis.ui.LayoutData;
import edu.uci.crayfis.ui.LayoutDeveloper;
import edu.uci.crayfis.ui.LayoutFeedback;
import edu.uci.crayfis.ui.LayoutHist;
import edu.uci.crayfis.ui.LayoutLeader;
import edu.uci.crayfis.ui.LayoutLogin;
import edu.uci.crayfis.ui.LayoutTime;
import edu.uci.crayfis.util.CFLog;

/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends AppCompatActivity {

    private static DAQService.DAQBinder mBinder;

    private final CFConfig CONFIG = CFConfig.getInstance();

    private ServiceConnection mServiceConnection;


	Context context;
    private Intent DAQIntent;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private final BroadcastReceiver FATAL_ERROR_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            final TextView tx1 = new TextView(context);
            tx1.setText(intent.getStringExtra(DAQService.EXTRA_ERROR_MESSAGE));
            tx1.setTextColor(Color.WHITE);
            tx1.setBackgroundColor(Color.BLACK);
            AlertDialog.Builder builder = new AlertDialog.Builder(DAQActivity.this);
            builder.setTitle(getResources().getString(R.string.fatal_error_title)).setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            System.exit(0);
                        }
                    })

                    .setView(tx1).show();
        }
    };

    ////////////////////////
    // Activity lifecycle //
    ////////////////////////


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            screen_brightness_mode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception e){ }
        Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        //Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS, 100);

        final File files[] = getFilesDir().listFiles();
        int foundFiles = 0;
        for (int i = 0; i < files.length && foundFiles < 5; i++) {
            if (files[i].getName().endsWith(".bin")) {
                new UploadExposureTask((CFApplication) getApplication(),
                        new UploadExposureService.ServerInfo(this), files[i])
                        .execute();
                foundFiles++;
            }
        }

        setContentView(R.layout.activity_daq);
        configureNavigation();

        context = getApplicationContext();

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(FATAL_ERROR_RECEIVER, new IntentFilter(DAQService.ACTION_FATAL_ERROR));

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mBinder = (DAQService.DAQBinder) iBinder;
                float time_sleeping = mBinder.getTimeWhileSleeping() * (float) 1e-3;
                int cand_sleeping = mBinder.getCountsWhileSleeping();
                if (time_sleeping > 5.0 && cand_sleeping > 0) {
                    Toast.makeText(context, "Your device saw " + cand_sleeping + " particle candidates in the last " + String.format("%1.1f", time_sleeping) + "s", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                mBinder = null;
            }
        };
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mActionBarDrawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        CFLog.d("DAQActivity onResume");

        // in case this isn't already running
        DAQIntent = new Intent(this, DAQService.class);
        startService(DAQIntent);
        bindService(DAQIntent, mServiceConnection, BIND_AUTO_CREATE);

        // check for any updates

        if (!CONFIG.getUpdateURL().equals("") && !CONFIG.getUpdateURL().equals(last_update_URL)) {
            showUpdateURL(CONFIG.getUpdateURL());

        }

        final View userStatus = findViewById(R.id.user_status);
        if (userStatus != null) {
            userStatus.postInvalidate();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        CFLog.i("onPause()");

        if(mBinder != null) {
            mBinder.saveStatsBeforeSleeping();
        }
        unbindService(mServiceConnection);
    }

    @Override
    protected void onStop() {
        super.onStop();

        CFLog.d("onStop()");

        // give back brightness control
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, screen_brightness_mode);

    }


    /////////////////////////
    // Toolbar and Drawers //
    /////////////////////////



    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.daq_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Check if the drawer toggle has already handled the click.  The hamburger icon is an option item.
        if (mActionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch(item.getItemId()) {
            case R.id.menu_settings:
                clickedSettings();
                return true;
            case R.id.menu_about:
                clickedAbout();
                return true;
            case R.id.menu_stop:
                clickedStop();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Configure the toolbar and navigation drawer.
     */
    private void configureNavigation() {
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        mActionBarDrawerToggle = new ActionBarDrawerToggle(this, (DrawerLayout) findViewById(R.id.drawer_layout), 0, 0);
        final DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        final NavHelper.NavDrawerListener listener = new NavHelper.NavDrawerListener(mActionBarDrawerToggle);
        drawerLayout.setDrawerListener(listener);
        final ListView navItems = (ListView) findViewById(R.id.nav_list_view);
        navItems.setAdapter(new NavDrawerAdapter(this));
        navItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                NavHelper.doNavClick(DAQActivity.this, view, listener, drawerLayout);
            }
        });

        // When the device is not registered, this should take the user to the log in page.
        findViewById(R.id.user_status).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (CONFIG.getAccountName() == null) {
                    NavHelper.setFragment(DAQActivity.this, new LayoutLogin(), null);
                    drawerLayout.closeDrawers();
                }
            }
        });

        final NavDrawerAdapter navItemsAdapter = new NavDrawerAdapter(this);
        navItems.setAdapter(navItemsAdapter);
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
                navItemsAdapter.notifyDataSetChanged();
            }
        });
        NavHelper.setFragment(this, DataCollectionFragment.getInstance(), NavDrawerAdapter.Type.STATUS.getTitle());
    }

    public void clickedSettings() {

        stopService(DAQIntent);
		Intent i = new Intent(this, UserSettingActivity.class);
		startActivity(i);
	}

    public void clickedAbout() {

        final SpannableString s = new SpannableString(
                "crayfis.io/about.html");

        final TextView tx1 = new TextView(this);

        // FIXME: Jodi - There has to be a better way, but this works.... Move these into the fragments or something....
        final List fragments = getSupportFragmentManager().getFragments();
        if (fragments.size() == 0) {
            return;
        }

        final Fragment activeFragment = (Fragment) fragments.get(0);
        if (activeFragment instanceof LayoutData) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+"\n"
                    +getResources().getString(R.string.help_data)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);
        } else if (activeFragment instanceof LayoutHist) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.help_hist)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s
            );
        } else if (activeFragment instanceof LayoutTime) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_dosimeter)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)

                    + s);
        } else if (activeFragment instanceof LayoutLogin) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_login)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);
        } else if (activeFragment instanceof LayoutLeader) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_leader)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);
        } else if (activeFragment instanceof LayoutDeveloper) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_devel)+"\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);
        } else if (activeFragment instanceof LayoutBlack) {
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_black)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);
        } else {
            tx1.setText("No more further information available at this time.");
        }


//        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.GALLERY)
//            tx1.setText(getResources().getString(R.string.crayfis_about)+
//                    "\n"+getResources().getString(R.string.toast_gallery)+"\n\n"+
//                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
//                    + s);
//
//
//        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.INACTIVE)

        tx1.setAutoLinkMask(RESULT_OK);
        tx1.setMovementMethod(LinkMovementMethod.getInstance());
        tx1.setTextColor(Color.WHITE);
        tx1.setBackgroundColor(Color.BLACK);
        Linkify.addLinks(s, Linkify.WEB_URLS);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle( getResources().getString(R.string.about_title)).setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })
                .setNegativeButton(getResources().getString(R.string.feedback), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        NavHelper.setFragment(DAQActivity.this, LayoutFeedback.getInstance(),
                                NavDrawerAdapter.Type.FEEDBACK.getTitle());
                    }
                })
                .setView(tx1).show();
    }

    private void clickedStop() {
        stopService(DAQIntent);
        finish();
    }

    private String last_update_URL = "";
    public void showUpdateURL(String url)
    {
        last_update_URL=url;
        final SpannableString s = new SpannableString(url);
        final TextView tx1 = new TextView(this);

        tx1.setText(getResources().getString(R.string.update_notice)+s);
        tx1.setAutoLinkMask(RESULT_OK);
        tx1.setMovementMethod(LinkMovementMethod.getInstance());
        tx1.setTextColor(Color.WHITE);
        tx1.setBackgroundColor(Color.BLACK);
        Linkify.addLinks(s, Linkify.WEB_URLS);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle( getResources().getString(R.string.update_title)).setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })

                .setView(tx1).show();
    }


    private int screen_brightness_mode=Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;


    ////////////////
    // UI Updates //
    ////////////////

    public static DAQService.DAQBinder getBinder() {
        return mBinder;
    }

}
