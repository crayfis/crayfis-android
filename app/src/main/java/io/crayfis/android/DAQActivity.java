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

package io.crayfis.android;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
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
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.crayfis.android.navdrawer.NavDrawerAdapter;
import io.crayfis.android.navdrawer.NavHelper;
import io.crayfis.android.ui.CFFragment;
import io.crayfis.android.ui.DataCollectionFragment;
import io.crayfis.android.ui.LayoutFeedback;
import io.crayfis.android.ui.LayoutLogin;
import io.crayfis.android.util.CFLog;

/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends AppCompatActivity {

    private DAQService.DAQBinder mBinder;

    private final CFConfig CONFIG = CFConfig.getInstance();

    private ServiceConnection mServiceConnection;

    private boolean mRestartAfterSettings = false;


	Context context;

    private Intent DAQIntent;

    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private final BroadcastReceiver ERROR_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String message = intent.getStringExtra(CFApplication.EXTRA_ERROR_MESSAGE);
            if(intent.getBooleanExtra(CFApplication.EXTRA_IS_FATAL, false)) {
                final TextView tx1 = new TextView(context);
                tx1.setText(message);
                tx1.setTextColor(Color.WHITE);
                tx1.setBackgroundColor(Color.BLACK);
                AlertDialog.Builder builder = new AlertDialog.Builder(DAQActivity.this);
                try {
                    builder.setTitle(getResources().getString(R.string.fatal_error_title)).setCancelable(false)
                            .setPositiveButton(getResources().getString(R.string.quit), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // notifications would be redundant
                                    NotificationManager notificationManager
                                            = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                                    notificationManager.cancelAll();
                                    System.exit(0);
                                }
                            })
                            .setNegativeButton(getResources().getString(R.string.keep_browsing), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setView(tx1).show();
                } catch(WindowManager.BadTokenException e) {
                    // DAQActivity is down
                    e.printStackTrace();
                    finish();
                }
            } else {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        }
    };

    ////////////////////////
    // Activity lifecycle //
    ////////////////////////


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_daq);
        configureNavigation();

        context = getApplicationContext();
        DAQIntent = new Intent(this, DAQService.class);

        // make sure we begin the service when the activity is created
        mRestartAfterSettings = true;

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

        final View userStatus = findViewById(R.id.user_status);
        if (userStatus != null) {
            userStatus.postInvalidate();
        }
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mActionBarDrawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // check whether we need to re-evaluate permissions
        if(!MainActivity.hasPermissions(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(ERROR_RECEIVER, new IntentFilter(CFApplication.ACTION_ERROR));

        // see if we are intentionally finished
        CFApplication application = (CFApplication) getApplication();
        if(application.getApplicationState() == CFApplication.State.FINISHED && !mRestartAfterSettings) return;
        mRestartAfterSettings = false;

        // if not, start the service
        startService(DAQIntent);
        bindService(DAQIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onPause() {
        super.onPause();

        CFLog.i("onPause()");

        if(mBinder != null) {
            mBinder.saveStatsBeforeSleeping();
        }
        try {
            unbindService(mServiceConnection);
        } catch (IllegalArgumentException e) {
            // service was not registered yet
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        CFLog.d("onStop()");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(ERROR_RECEIVER);
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
    public boolean onPrepareOptionsMenu(final Menu menu) {
        MenuItem startStopButton = menu.findItem(R.id.menu_start_stop);
        CFApplication application = (CFApplication) getApplication();
        if(application.getApplicationState() == CFApplication.State.FINISHED) {
            startStopButton.setIcon(R.drawable.ic_action_resume);
            startStopButton.setTitle(R.string.menu_resume);
        } else {
            startStopButton.setIcon(R.drawable.ic_action_pause);
            startStopButton.setTitle(R.string.menu_stop);
        }
        return super.onPrepareOptionsMenu(menu);
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
            case R.id.menu_start_stop:
                CFApplication application = (CFApplication) getApplicationContext();
                if(application.getApplicationState() == CFApplication.State.FINISHED) {
                    clickedStart();
                } else {
                    clickedStop();
                }
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
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

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
        NavHelper.setFragment(this, new DataCollectionFragment(), NavDrawerAdapter.Type.STATUS.getTitle());
    }

    public void clickedSettings() {

        CFApplication application = (CFApplication) getApplication();
        CFApplication.State state = application.getApplicationState();

        if(state != CFApplication.State.FINISHED) {
            application.setApplicationState(CFApplication.State.FINISHED);
            mRestartAfterSettings = true;
        }
		Intent i = new Intent(this, UserSettingActivity.class);
		startActivity(i);
	}

    public void clickedAbout() {

        final SpannableString s = new SpannableString(
                "crayfis.io/about.html");

        final TextView tx1 = new TextView(this);

        final List fragments = getSupportFragmentManager().getFragments();
        if (fragments.size() == 0) {
            return;
        }

        final CFFragment activeFragment = (CFFragment) fragments.get(fragments.size()-1);

        final Resources res = getResources();
        tx1.setText(res.getString(R.string.crayfis_about) + "\n"
                + res.getString(activeFragment.about()) + "\n\n"
                + res.getString(R.string.swipe_help) + "\n"
                + res.getString(R.string.more_details) + " " + s);


        tx1.setAutoLinkMask(RESULT_OK);
        tx1.setMovementMethod(LinkMovementMethod.getInstance());
        tx1.setTextColor(Color.WHITE);
        tx1.setBackgroundColor(Color.BLACK);
        Linkify.addLinks(s, Linkify.WEB_URLS);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.about_title) + " ("
                + ((CFApplication)getApplication()).getBuildInformation().getBuildVersion() + ")")
                .setCancelable(false)
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
        CFApplication application = (CFApplication)getApplication();
        application.setApplicationState(CFApplication.State.FINISHED);
        invalidateOptionsMenu();
    }

    private void clickedStart() {
        CFLog.d("clickedStart()");
        invalidateOptionsMenu();

        startService(DAQIntent);
        bindService(DAQIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    ////////////////
    // UI Updates //
    ////////////////

    public DAQService.DAQBinder getBinder() {
        return mBinder;
    }

}
