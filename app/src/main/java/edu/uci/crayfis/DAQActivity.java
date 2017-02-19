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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
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

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.navdrawer.NavDrawerAdapter;
import edu.uci.crayfis.navdrawer.NavHelper;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.server.UploadExposureTask;
import edu.uci.crayfis.trigger.L1Processor;
import edu.uci.crayfis.trigger.L2Processor;
import edu.uci.crayfis.trigger.L2Task;
import edu.uci.crayfis.ui.DataCollectionFragment;
import edu.uci.crayfis.util.CFLog;
import edu.uci.crayfis.widget.DataCollectionStatsView;

/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends AppCompatActivity {

    private LayoutBlack mLayoutBlack = LayoutBlack.getInstance();
    private LayoutHist mLayoutHist = LayoutHist.getInstance();
    private LayoutTime mLayoutTime = LayoutTime.getInstance();
    private LayoutDeveloper mLayoutDeveloper = LayoutDeveloper.getInstance();

    private final CFConfig CONFIG = CFConfig.getInstance();

    private Timer mUiUpdateTimer;

	// keep track of how often we had to drop a frame at L1
	// because the L2 queue was full.
    // FIXME This is wrong to be static.
	public static int L2busy = 0;

    private L1Processor mL1Processor = null;
    private L2Processor mL2Processor = null;
    private L1Calibrator L1cal = null;

	// L1 hit threshold
	long starttime;

    // store value of most recently calculated FPS
    private double last_fps = 0;

	Context context;
    private ActionBarDrawerToggle mActionBarDrawerToggle;

    public void clickedSettings() {

		Intent i = new Intent(this, UserSettingActivity.class);
		startActivity(i);
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

    private long sleeping_since=0;
    private int cands_before_sleeping=0;
    public void goToSleep()
    {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();

        try {
            screen_brightness = getWindow().getAttributes().screenBrightness;
           // CFLog.d(" saving screen brightness of "+screen_brightness);
        } catch (Exception e) {
            CFLog.d(" Unable to find screen brightness");
            screen_brightness = -1;
        }

        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawers();
        NavHelper.setFragment(this, LayoutBlack.getInstance(), NavDrawerAdapter.Type.LIVE_VIEW.getTitle());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // Newer devices allow us to completely hide the soft control buttons.
            // Doesn't matter what view is used here, we just need the methods in the View class.
            final View view = findViewById(R.id.fragment_container);
            view.setOnSystemUiVisibilityChangeListener(new UiVisibilityListener());
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        sleep_mode=true;
        sleeping_since = System.currentTimeMillis();
        cands_before_sleeping = mL2Processor.mL2Count;
    }

    public void clickedSleep()
    {
        // go to sleep
        goToSleep();
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

    // last time we warned about the location
    private long last_location_warning=0;


    private int screen_brightness_mode=Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

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

		starttime = System.currentTimeMillis();
        last_user_interaction = starttime;
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

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mActionBarDrawerToggle.syncState();
    }


    @Override
    protected void onStop() {
        super.onStop();

        // give back brightness control
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, screen_brightness_mode);


    }


	@Override
	protected void onResume() {
		super.onResume();

        CFLog.d("DAQActivity onResume: last user interaction="+last_user_interaction);

        if (mUiUpdateTimer != null) {
            mUiUpdateTimer.cancel();
        }
        mUiUpdateTimer = new Timer();
        mUiUpdateTimer.schedule(new UiUpdateTimerTask(), 1000L, 1000L);

        DAQService.startService(this);
    }

	@Override
	protected void onPause() {
		super.onPause();

        DAQService.endService();

        mUiUpdateTimer.cancel();

        CFLog.i("DAQActivity Suspending!");

        DataCollectionFragment.getInstance().updateIdleStatus("");

        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.IDLE);


	}

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
            case R.id.menu_sleep:
                clickedSleep();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void userErrorMessage(String mess, boolean fatal)
    {
        final TextView tx1 = new TextView(this);
        tx1.setText(mess);
        tx1.setTextColor(Color.WHITE);
        tx1.setBackgroundColor(Color.BLACK);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.fatal_error_title)).setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })

                .setView(tx1).show();
        finish();
        if (fatal)
            System.exit(0);
    }



    private long last_user_interaction=0;
    private boolean sleep_mode = false;
    private float screen_brightness = 0;
    @Override
    public void onUserInteraction()
    {
        last_user_interaction=System.currentTimeMillis();
       // CFLog.d(" The UserActivity at time= "+last_user_interaction);
        if (sleep_mode)
        {
            //wake up
            getSupportActionBar().show();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            NavHelper.setFragment(this, DataCollectionFragment.getInstance(), NavDrawerAdapter.Type.STATUS.getTitle());

          //  CFLog.d(" Switching out of INACTIVE pane to pane "+previous_item+" and setting brightness to "+screen_brightness);

            // Set the screen brightness to what we have saved.  We should have retrieved a value less than
            // 0, meaning the user themselves set their screen brightness.  Failure on that, let's err on the side
            // of caution and not blind them at 3am.  0 means super dark, not off.
            WindowManager.LayoutParams settings = getWindow().getAttributes();
            settings.screenBrightness = screen_brightness > 0 ? 0 : screen_brightness;
            getWindow().setAttributes(settings);
            sleep_mode=false;

            long current_time = System.currentTimeMillis();
            float time_sleeping = (current_time-sleeping_since)*(float)1e-3;
            int cand_sleeping = 0; //mL2Processor.mL2Count-cands_before_sleeping;
            if (time_sleeping > 5.0)
            {
                Toast.makeText(this, "Your device saw "+cand_sleeping+" particle candidates in the last "+ String.format("%1.1f",time_sleeping)+"s",Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Task that gets called during the UI update tick.
     */
    private final class UiUpdateTimerTask extends TimerTask {


        private final Runnable RUNNABLE = new Runnable() {
            @Override
            public void run() {
                final CFApplication application = (CFApplication) getApplication();
                SharedPreferences localPrefs = PreferenceManager.getDefaultSharedPreferences(application);
                boolean disable_sleep = localPrefs.getBoolean("prefDisableScreenSaver", false);

                //CFLog.d(" The last recorded user interaction was at "+((last_user_interaction - System.currentTimeMillis())/1e3)+" sec ago");
                if (!disable_sleep && !sleep_mode
                        && (System.currentTimeMillis() - last_user_interaction) >= CFApplication.SLEEP_TIMEOUT_MS
                        && (application.getApplicationState() == CFApplication.State.DATA
                            || application.getApplicationState() == CFApplication.State.IDLE)
                        ) // wait 1m after going into DATA or IDLE mode
                {
                    goToSleep();

                }

                // turn on developer options if it has been selected
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                //l2thread.setmSaveImages(sharedPrefs.getBoolean("prefEnableGallery", false));

                // Originally, the updating of the LevelView was done here.  This seems like a good place to also
                // make sure that UserStatusView gets updated with any new counts.
                final View userStatus = findViewById(R.id.user_status);
                if (userStatus != null) {
                    userStatus.postInvalidate();
                }

                try {

                    final Camera.Size cameraSize = CFApplication.getCameraSize();

                    if (LayoutData.mLightMeter != null) {
                        LayoutData.updateData();
                    }

                    if (application.getApplicationState() == CFApplication.State.IDLE)
                    {
                        if (LayoutData.mProgressWheel != null) {
                            LayoutData.mProgressWheel.setText("");

                            LayoutData.mProgressWheel.setTextColor(Color.WHITE);
                            LayoutData.mProgressWheel.setBarColor(Color.LTGRAY);

                            int progress = 0; //(int) (360 * batteryPct);
                            LayoutData.mProgressWheel.setProgress(progress);
                            LayoutData.mProgressWheel.stopGrowing();
                            LayoutData.mProgressWheel.doNotShowBackground();
                        }
                    }


                    if (application.getApplicationState() == CFApplication.State.STABILIZATION)
                    {
                        if (LayoutData.mProgressWheel != null) {
                            LayoutData.mProgressWheel.setText(getResources().getString(R.string.stabilization));

                            LayoutData.mProgressWheel.setTextColor(Color.RED);
                            LayoutData.mProgressWheel.setBarColor(Color.RED);

                            LayoutData.mProgressWheel.stopGrowing();
                            LayoutData.mProgressWheel.spin();
                            LayoutData.mProgressWheel.doNotShowBackground();
                        }
                    }


                    if (application.getApplicationState() == CFApplication.State.CALIBRATION)
                    {
                        if (LayoutData.mProgressWheel != null) {

                            LayoutData.mProgressWheel.setText(getResources().getString(R.string.calibration));

                            LayoutData.mProgressWheel.setTextColor(Color.RED);
                            LayoutData.mProgressWheel.setBarColor(Color.RED);

                            int needev = CONFIG.getCalibrationSampleFrames();
                            float frac = L1cal.getMaxPixels().size() / ((float) 1.0 * needev);
                            int progress = (int) (360 * frac);
                            LayoutData.mProgressWheel.setProgress(progress);
                            LayoutData.mProgressWheel.stopGrowing();
                            LayoutData.mProgressWheel.showBackground();


                        }
                    }
                    if (application.getApplicationState() == CFApplication.State.DATA)
                    {
                        if (LayoutData.mProgressWheel != null) {
                            LayoutData.mProgressWheel.setText(getResources().getString(R.string.taking_data));
                            LayoutData.mProgressWheel.setTextColor(0xFF00AA00);
                            LayoutData.mProgressWheel.setBarColor(0xFF00AA00);

                            // solid circle
                            LayoutData.mProgressWheel.setProgress(360);
                            LayoutData.mProgressWheel.showBackground();
                            LayoutData.mProgressWheel.grow();

                        }

                        if (mL2Processor != null) {
                            final DataCollectionStatsView.Status dstatus = new DataCollectionStatsView.Status.Builder()
                                    .setTotalEvents(mL2Processor.mL2Count)
                                    .setTotalPixels((long)mL1Processor.mL1CountData * cameraSize.height * cameraSize.width)
                                    .setTotalFrames(mL1Processor.mL1CountData)
                                    .build();
                            CFApplication.setCollectionStatus(dstatus);
                        }

                        boolean show_splashes = sharedPrefs.getBoolean("prefSplashView", true);
                        if (show_splashes && mLayoutBlack != null) {
                            try {
                                L2Task.RecoEvent ev = null; //l2thread.getDisplayPixels().poll(10, TimeUnit.MILLISECONDS);
                                if (ev != null) {
                                    //CFLog.d(" L2thread poll returns an event with " + ev.pixels.size() + " pixels time=" + ev.time + " pv =" + previewSize);
                                    mLayoutBlack.addEvent(ev);
                                } else {
                                    // CFLog.d(" L2thread poll returns null ");
                                }

                            } catch (Exception e) {
                                // just don't do it
                            }
                        }

                        if (mLayoutTime != null) mLayoutTime.updateData();
                        if (mLayoutHist != null) mLayoutHist.updateData();

                        if (mLayoutDeveloper == null)
                            mLayoutDeveloper = (LayoutDeveloper) LayoutDeveloper.getInstance();

                    }

                    if (mLayoutDeveloper != null) {
                        if (mLayoutDeveloper.mAppBuildView != null)
                            mLayoutDeveloper.mAppBuildView.setAppBuild(application.getBuildInformation());


                        if (mLayoutDeveloper.mTextView != null) {
                            String devtxt = "";
                            mLayoutDeveloper.mTextView.setText(devtxt);
                        }
                    }

                    if (CONFIG.getUpdateURL() != "" && CONFIG.getUpdateURL() != last_update_URL) {
                        showUpdateURL(CONFIG.getUpdateURL());

                    }
                } catch (OutOfMemoryError e) { // don't crash of OOM, just don't update UI

                }
            }
        };

        @Override
        public void run() {
            runOnUiThread(RUNNABLE);
        }
    }

    //FIXME: THIS CLASS IS WAY TOO BIG
    private final class UiVisibilityListener implements View.OnSystemUiVisibilityChangeListener {

        @Override
        public void onSystemUiVisibilityChange(final int visibility) {
            if (visibility == 0) {
                // This is so the user doesn't have to double tap the screen to get back to normal.
                onUserInteraction();
            }
        }
    }
}
