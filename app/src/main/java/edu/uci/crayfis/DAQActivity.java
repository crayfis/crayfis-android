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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.support.annotation.NonNull;
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
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import edu.uci.crayfis.calibration.FrameHistory;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.camera.AcquisitionTime;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.camera.ResolutionSpec;
import edu.uci.crayfis.exception.IllegalFsmStateException;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.exposure.ExposureBlockManager;
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
public class DAQActivity extends AppCompatActivity implements Camera.PreviewCallback, SensorEventListener,
        ConnectionCallbacks, OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private static LayoutBlack mLayoutBlack = LayoutBlack.getInstance();
    private static LayoutHist mLayoutHist = LayoutHist.getInstance();
    private static LayoutTime mLayoutTime = LayoutTime.getInstance();
    private static LayoutDeveloper mLayoutDeveloper = LayoutDeveloper.getInstance();

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication.AppBuild mAppBuild;

    // camera and display objects
	private Camera mCamera;
    private Camera.Parameters mParams;
    private Camera.Size previewSize;
    private SurfaceTexture mTexture;

    // helper that dispatches L1 inputs to be processed by the L1 trigger.
    private L1Processor mL1Processor = null;
    // helper that dispatches L2 inputs to be processed by the L2 trigger.
    private L2Processor mL2Processor = null;


    private Timer mUiUpdateTimer;

    // ----8<--------------



	// WakeLock to prevent the phone from sleeping during DAQ
	private PowerManager.WakeLock wl;

    public final float battery_stop_threshold = 0.20f;
    public final float battery_start_threshold = 0.80f;
    public final int batteryOverheatTemp = 450;
    public final int batteryStartTemp = 380;

    public static final int N_CYCLE_BUFFERS = 10;

    DataProtos.RunConfig run_config = null;

	// sensors
	SensorManager mSensorManager;
	private Sensor gravSensor = null;
	private Sensor accelSensor = null;
	private Sensor magSensor = null;
	private float[] gravity = new float[3];
	private float[] geomagnetic = new float[3];
	private float[] orientation = new float[3];

	// keep track of how often we had to drop a frame at L1
	// because the L2 queue was full.
    // FIXME This is wrong to be static.
	public static int L2busy = 0;

	private ExposureBlockManager xbManager;

    private L1Calibrator L1cal = null;

    private FrameHistory<Long> frame_times;

	// L1 hit threshold
	long starttime;

    // number of frames to wait between fps updates
	public static final int fps_update_interval = 20;

    // store value of most recently calculated FPS
    private double last_fps = 0;

    // Thread for NTP updates
    private SntpUpdateThread ntpThread;

	Context context;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private RenderScript mRS;
    private Type mType;
    private ScriptIntrinsicHistogram mScript;

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

    private boolean location_valid(Location location)
    {

        return (location != null
                && java.lang.Math.abs(location.getLongitude())>0.1
                && java.lang.Math.abs(location.getLatitude())>0.1);

    }

	private void newLocation(Location location, boolean deprecated)
    {

        if (deprecated==false)
        {
            //  Google location API
            // as long as it's valid, update the data
            if (location != null)
                CFApplication.setLastKnownLocation(location);
        } else {
            // deprecated interface as backup

            // is it valid?
            if (location_valid(location))
            {
                // do we not have a valid current location?
                if (location_valid(CFApplication.getLastKnownLocation()) == false)
                {
                    // use the deprecated info if it's the best we have
                    CFApplication.setLastKnownLocation(location);
                }
        }

        }
        //CFLog.d("## newLocation data "+location+" deprecated? "+deprecated+" -> current location is "+CFApplication.getLastKnownLocation());

    }

	private void doStateTransitionCalibration(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
		// The *only* valid way to get into calibration mode
		// is after stabilizaton.
		switch (previousState) {
            case STABILIZATION:
                L1cal.clear();
                frame_times.clear();
                xbManager.newExposureBlock(CFApplication.State.CALIBRATION);
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + ((CFApplication) getApplication()).getApplicationState());
        }
	}

    private void doStateTransitionReconfigure(@NonNull final CFApplication.State previousState) {
        // Note: we can enter reconfigure from any state

        switch (previousState) {
            case DATA:
            case CALIBRATION:
                // make sure that calibration and data XB's get marked as aborted.
                xbManager.abortExposureBlock();
                break;
            default:
                // just invalidate any existing XB by creating a "dummy" block
                xbManager.newExposureBlock(CFApplication.State.RECONFIGURE);
                break;
        }

        // tear down and then reconfigure the camera
        unSetupCamera();
        setUpAndConfigureCamera();

        final CFApplication app = (CFApplication)getApplicationContext();
        // if we were idling, go back to that state.
        if (previousState == CFApplication.State.IDLE) {
            app.setApplicationState(CFApplication.State.IDLE);
        } else {
            // otherwise, re-enter the calibration loop.
            app.setApplicationState(CFApplication.State.STABILIZATION);
        }
    }

    // last time we warned about the location
    private long last_location_warning=0;

    /**
     * Set up for the transition to {@link edu.uci.crayfis.CFApplication.State#DATA}
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
	private void doStateTransitionData(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {

        // reset the location warning, so we start the 5 min clock on that
        last_location_warning = System.currentTimeMillis();

        // start the user interaction clock
        last_user_interaction = System.currentTimeMillis();
        switch (previousState) {
            /*
            case INIT:
                //l2thread.setFixedThreshold(true);
                xbManager.newExposureBlock(CFApplication.State.DATA);

                break;
            */
            case CALIBRATION:
                int new_thresh = calculateL1Threshold();
                // build the calibration result object
                DataProtos.CalibrationResult.Builder cal = DataProtos.CalibrationResult.newBuilder();

                for (int v : L1cal.getHistogram()) {
                    cal.addHistMaxpixel(v);
                }

                // and commit it to the output stream
                CFLog.i("DAQActivity Committing new calibration result.");
                UploadExposureService.submitCalibrationResult(this, cal.build());

                // update the thresholds
                CFLog.i("DAQActivity Setting new L1 threshold: {" + CONFIG.getL1Threshold() + "} -> {" + new_thresh + "}");
                CONFIG.setL1Threshold(new_thresh);

                // FIXME: we should have a better calibration for L2 threshold.
                // For now, we choose it to be just below L1thresh.
                final int l1Threshold = CONFIG.getL1Threshold();
                if (l1Threshold > 2) {
                    CONFIG.setL2Threshold(l1Threshold - 1);
                } else {
                    // Okay, if we're getting this low, we shouldn't try to
                    // set the L2thresh any lower, else event frames will be huge.
                    CONFIG.setL2Threshold(l1Threshold);
                }

                // Finally, set the state and start a new xb
                xbManager.newExposureBlock(CFApplication.State.DATA);

                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + ((CFApplication) getApplication()).getApplicationState());
        }

	}

    double target_L1_eff;
    public int calculateL1Threshold() {
        double fps = updateFPS();
        if (fps == 0) {
            CFLog.w("Warning! Got 0 fps in threshold calculation.");
        }
        target_L1_eff = ((double) CONFIG.getTargetEventsPerMinute()) / 60.0 / getFPS();
        return L1cal.findL1Threshold(target_L1_eff);
    }

    /**
     * We go to stabilization mode in order to wait for the camera to settle down after a period of bad data.
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
	private void doStateTransitionStabilization(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
		switch(previousState) {
            case INIT:
            case IDLE:
            case RECONFIGURE:
                // This is the first state transisiton of the app. Go straight into stabilization
                // so the calibratoin will be clean.
                /*
                if (l2thread != null)
                {
                l2thread.setFixedThreshold(false);
                l2thread.clearQueue();
                }
                */
                xbManager.newExposureBlock(CFApplication.State.STABILIZATION);
                break;

            // coming out of calibration and data should be the same.
            case CALIBRATION:
            case DATA:
                //l2thread.clearQueue();
                xbManager.abortExposureBlock();
                break;
            case STABILIZATION:
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + ((CFApplication) getApplication()).getApplicationState());
        }
	}

    /**
     * This mode is basically just used to cleanly close out any current exposure blocks.
     * For example, we transition here when the phone is locked or the app is suspended.
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
	private void doStateTransitionIdle(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        unSetupCamera();

        switch(previousState) {
            case CALIBRATION:
            case DATA:
                // for calibration or data, mark the block as aborted
                xbManager.abortExposureBlock();
                break;
            case STABILIZATION:
                // for data, stop taking data to let battery charge
                xbManager.newExposureBlock(CFApplication.State.IDLE);
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + ((CFApplication) getApplication()).getApplicationState());
        }
	}

	public void generateRunConfig() {
        long run_start_time = System.currentTimeMillis();
        long run_start_time_nano = System.nanoTime() - 1000000 * run_start_time; // reference start time to unix epoch

        CFApplication.setStartTimeNano(run_start_time_nano);

		DataProtos.RunConfig.Builder b = DataProtos.RunConfig.newBuilder();

        final UUID runId = mAppBuild.getRunId();
		b.setIdHi(runId.getMostSignificantBits());
		b.setIdLo(runId.getLeastSignificantBits());
		b.setCrayfisBuild(mAppBuild.getBuildVersion());
		b.setStartTime(run_start_time);

        /* get a bunch of camera info */
        b.setCameraParams(mParams.flatten());




        /* get a bunch of hw build info */
        String hw_params = "";
        hw_params += "build-board=" + Build.BOARD + ";";
        hw_params += "build-brand=" + Build.BRAND + ";";
        hw_params += "build-cpu-abi=" + Build.CPU_ABI + ";";
        hw_params += "build-cpu-abi2=" + Build.CPU_ABI2 + ";";
        hw_params += "build-device=" + Build.DEVICE + ";";
        hw_params += "build-display=" + Build.DISPLAY + ";";
        hw_params += "build-fingerprint=" + Build.FINGERPRINT + ";";
        hw_params += "build-hardware=" + Build.HARDWARE + ";";
        hw_params += "build-host=" + Build.HOST + ";";
        hw_params += "build-id=" + Build.ID + ";";
        hw_params += "build-manufacturer=" + Build.MANUFACTURER + ";";
        hw_params += "build-model=" + Build.MODEL + ";";
        hw_params += "build-radio=" + Build.RADIO + ";";
        hw_params += "build-serial=" + Build.SERIAL + ";";
        hw_params += "build-board=" + Build.BOARD + ";";
        hw_params += "build-tags=" + Build.TAGS + ";";
        hw_params += "build-time=" + Build.TIME + ";";
        hw_params += "build-type=" + Build.TYPE + ";";
        hw_params += "build-user=" + Build.USER + ";";
        b.setHwParams(hw_params);
        CFLog.i("SET HWPARAMS = " + hw_params);

        /* get a bunch of os build info */
        String os_params = "";
        os_params += "version-codename=" + Build.VERSION.CODENAME + ";";
        os_params += "version-incremental=" + Build.VERSION.INCREMENTAL + ";";
        os_params += "version-release=" + Build.VERSION.RELEASE + ";";
        os_params += "version-sdk-int=" + Build.VERSION.SDK_INT + ";";
        b.setOsParams(os_params);
        CFLog.i("SET OSPARAMS = " + os_params);

		run_config = b.build();
	}

    // location manager

    GoogleApiClient mGoogleApiClient;

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        CFLog.d("Build API client:"+mGoogleApiClient);
    }

    Location mLastLocation;
    LocationRequest mLocationRequest;

    @Override
    public void onConnected(Bundle connectionHint) {

        // first get last known location
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        CFLog.d("onConnected: asking for location = "+mLastLocation);

        // set the location; if this is false newLocation() will disregard it
        newLocation(mLastLocation,false);

        // request updates as well
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);


    }

    // Google location update interface
    public void onLocationChanged(Location location)
    {
        //CFLog.d("onLocationChanged: new  location = "+mLastLocation);

        // set the location; if this is false newLocation() will disregard it
        newLocation(location,false);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
    }



    // deprecated location stuff
    android.location.LocationListener mLocationListener = null;
    Location mLastLocationDeprecated;

    public Location getLocationDeprecated()
    {
        if (mLocationListener==null) {
            mLocationListener = new android.location.LocationListener() {
                // deprecated location update interface
                public void onLocationChanged(Location location) {
                    // Called when a new location is found by the network location
                    // provider.
                    //CFLog.d("onLocationChangedDeprecated: new  location = "+location);

                    mLastLocationDeprecated = location;

                    // update the location in case the Google method failed
                    newLocation(location,true);

                }

                public void onStatusChanged(String provider, int status,
                                            Bundle extras) {
                }

                public void onProviderEnabled(String provider) {
                }

                public void onProviderDisabled(String provider) {
                }
            };
        }
        // get the manager
        LocationManager locationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);

        // ask for updates from network and GPS
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        } catch (RuntimeException e)
        { // some phones do not support
        }
        // get the last known coordinates for an initial value
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (null == location) {
            location = new Location("BLANK");
        }
        return location;
    }

    private int screen_brightness_mode=Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        CFApplication application = (CFApplication) getApplication();

        mL2Processor = new L2Processor(application);
        mL1Processor = new L1Processor(application);
        mL1Processor.setL2Processor(mL2Processor);

        /*
        TODO: Flush the UploadExposureService queue.

        The idea here is that if there are files left in the queue, whenever this activity is
        relaunched, that would be a good time to try to flush it.  This avoids background
        watchers and makes it more probible that the data is uploaded at a time that is
        convenient to the user.
         */

        mAppBuild = application.getBuildInformation();

        CFLog.d("  Yet more newer system settings stuff ");


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


        // to keep DAQ going without taking over the phone.
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        // = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,"SDWL");

		// get the grav/accelerometer, if any
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		gravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

		/*
		// clear saved settings (for debug purposes)
		SharedPreferences.Editor editor = localPrefs.edit();
		editor.clear();
		editor.commit();
		*/

        setContentView(R.layout.activity_daq);
        configureNaviation();

		context = getApplicationContext();

        if (L1cal == null) {
            L1cal = L1Calibrator.getInstance();
        }
        if (frame_times == null) {
            frame_times = new FrameHistory<Long>(100);
        }

		starttime = System.currentTimeMillis();
        last_battery_check_time = 0; // this will trigger measuring the battery level the first time
        batteryPct = -1; // indicate no data yet
        last_user_interaction = starttime;
        /////////
        newLocation(new Location("BLANK"), false);
        buildGoogleApiClient(); // connect() and disconnect() called in onStart() and onStop()

        // backup location if Google play isn't working or installed
        mLastLocationDeprecated = getLocationDeprecated();
        newLocation(mLastLocationDeprecated, true);

		xbManager = ExposureBlockManager.getInstance(this);

        LocalBroadcastManager.getInstance(this).registerReceiver(STATE_CHANGE_RECEIVER,
                new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
	}

    /**
     * Configure the toolbar and navigation drawer.
     */
    private void configureNaviation() {
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
    protected void onStart()
    {
        super.onStart();
        CFLog.d("CALL: onStart!");
        mGoogleApiClient.connect();
        CFLog.d("DAQActivity onStart: wake lock held?"+wl.isHeld());


    }

    private void unSetupCamera() {
        CFLog.d(" DAQActivity: unsetup camera called. mCamera ="+mCamera);

        // stop the camera preview and all processing
        if (mCamera != null) {

            if (N_CYCLE_BUFFERS>0) {
                mCamera.setPreviewCallbackWithBuffer(null);
            } else {
                mCamera.setPreviewCallback(null);
            }
            mCamera.stopPreview();
            mCamera.release();
            mTexture = null;
            mParams = null;
            mCamera = null;

            // clear out any (old) buffers
            //l2thread.clearQueue();
            // FIXME: should we abort any AsyncTasks in the L1/L2 queue that haven't executed yet?

            CFLog.d(" DAQActivity: unsetup camera");
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        CFLog.d("DAQActivity onStop: wake lock held?"+wl.isHeld());

        if (wl.isHeld())
            wl.release();

        mGoogleApiClient.disconnect();

        ///  Moved from onPause

        CFLog.i("DAQActivity Suspending!");

        unSetupCamera();
        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.IDLE);

        // give back brightness control
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, screen_brightness_mode);


    }

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// make sure we're in IDLE state, to make sure
		// everything's closed.
        // FIXME This might not be valid if things are running in the background.
        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.IDLE);

		// flush all the closed exposure blocks immediately.
		xbManager.flushCommittedBlocks(true);

		// stop the image processing thread.
		//l2thread.stopThread();
		//l2thread = null;
        // FIXME: should we abort any AsyncTasks in the L1/L2 queue that haven't executed yet?

        ntpThread.stopThread();
        ntpThread = null;

        LocalBroadcastManager.getInstance(this).unregisterReceiver(STATE_CHANGE_RECEIVER);
	}





	@Override
	protected void onResume() {
		super.onResume();





		if (!wl.isHeld()) wl.acquire();
        CFLog.d("DAQActivity onResume: last user interaction="+last_user_interaction);

		Sensor sens = gravSensor;
		if (sens == null) {
			sens = accelSensor;
		}
        CFLog.d(" register sensors");
		mSensorManager.registerListener(this, sens, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);

        CFLog.d(" on resume camera = "+mCamera);

        if (mCamera != null)
			throw new RuntimeException(
					"Bug, camera should not be initialized already");


        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        CFLog.d(" Build RenderScript");
        mRS = RenderScript.create(context);

        CFLog.d(" Setup camera");
        // this will also trigger setting up the camera
        setUpAndConfigureCamera();
        CFLog.d(" Trigger stabilization state");

        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.STABILIZATION);



        mSntpClient = SntpClient.getInstance();

		// once all the hardware is set up and the output manager is running,
		// we can generate and commit the runconfig
		if (run_config == null) {
			generateRunConfig();
            UploadExposureService.submitRunConfig(this, run_config);
		}

        if (mUiUpdateTimer != null) {
            mUiUpdateTimer.cancel();
        }
        mUiUpdateTimer = new Timer();
        mUiUpdateTimer.schedule(new UiUpdateTimerTask(), 1000l, 1000l);

    }

	@Override
	protected void onPause() {
		super.onPause();

        mUiUpdateTimer.cancel();

        CFLog.d("DAQActivity onPause: wake lock held?" + wl.isHeld());


        mSensorManager.unregisterListener(this);


        CFLog.i("DAQActivity Suspending!");

        unSetupCamera();

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

    /**
	 * Sets up the camera if it is not already setup.
	 */
	private void setUpAndConfigureCamera() {
		// Open and configure the camera
        CFLog.d("setUpAndConfigureCamera()");
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            userErrorMessage(getResources().getString(R.string.camera_error),true);

        }
        CFLog.d("Camera opened camera="+mCamera);
        try {

            Camera.Parameters param= mCamera.getParameters();

            previewSize = CONFIG.getTargetResolution().getClosestSize(mCamera);
            CFLog.i("selected preview size="+previewSize);

            if (ntpThread == null) {
                // FIXME: why do we do this in SetupAndConfigureCamera()? Why not onCreate or onResume?
                ntpThread = new SntpUpdateThread();
                ntpThread.start();
            }

            CFLog.d("setup: size is width=" + previewSize.width + " height =" + previewSize.height);
            param.setPreviewSize(previewSize.width, previewSize.height);
            // param.setFocusMode("FIXED");
            param.setExposureCompensation(0);

            // Try to pick the highest FPS range possible
            int minfps = 0, maxfps = 0;
            List<int[]> validRanges = param.getSupportedPreviewFpsRange();
            if (validRanges != null)
                for (int[] rng : validRanges) {
                    CFLog.i("DAQActivity Supported FPS range: [ " + rng[0] + ", " + rng[1] + " ]");
                    if (rng[1] > maxfps || (rng[1] == maxfps && rng[0] > minfps)) {
                        maxfps = rng[1];
                        minfps = rng[0];
                    }
                }
            CFLog.i("DAQActivity Selected FPS range: [ " + minfps + ", " + maxfps + " ]");
            try{
                // Try to set minimum=maximum FPS range.
                // This seems to work for some phones...
                param.setPreviewFpsRange(maxfps, maxfps);
            }
            catch (RuntimeException ex) {
                // but some phones will throw a fit. So just give it a "supported" range.
                CFLog.w("DAQActivity Unable to set maximum frame rate. Falling back to default range.");
                param.setPreviewFpsRange(minfps, maxfps);
            }
            mParams = param;
            mCamera.setParameters(mParams);
            CFLog.d("params: Camera params are " + param.flatten());

            // update the current application settings to reflect new camera size.
            CFApplication.setCameraSize(mParams.getPreviewSize());

            // configure RenderScript if available
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mScript = ScriptIntrinsicHistogram.create(mRS, Element.U8(mRS));
                Type.Builder tb = new Type.Builder(mRS, Element.createPixel(mRS, Element.DataType.UNSIGNED_8, Element.DataKind.PIXEL_YUV));
                tb.setX(previewSize.width)
                        .setY(previewSize.height)
                        .setYuvFormat(param.getPreviewFormat());
                mType = tb.create();
            }

            } catch (Exception e) {
                userErrorMessage(getResources().getString(R.string.camera_error),true);

        }

        try {
            int bufSize = previewSize.width*previewSize.height* ImageFormat.getBitsPerPixel(mParams.getPreviewFormat())/8;
            if(N_CYCLE_BUFFERS > 0) {
                mCamera.setPreviewCallbackWithBuffer(this);
                for(int i=0; i<N_CYCLE_BUFFERS; i++) {
                    mCamera.addCallbackBuffer(new byte[bufSize]);
                }
            } else {
                mCamera.setPreviewCallback(this);
            }
            mTexture = new SurfaceTexture(10);
            mCamera.setPreviewTexture(mTexture);
            mCamera.startPreview();
        }  catch (Exception e) {
            userErrorMessage(getResources().getString(R.string.camera_error),true);
        }
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
            int cand_sleeping = mL2Processor.mL2Count-cands_before_sleeping;
            if (time_sleeping > 5.0)
            {
                Toast.makeText(this, "Your device saw "+cand_sleeping+" particle candidates in the last "+ String.format("%1.1f",time_sleeping)+"s",Toast.LENGTH_LONG).show();
            }
        }
    }


    private SntpClient mSntpClient = null;


    private final long battery_check_wait = 10000; // ms
    private long last_battery_check_time;
    private float batteryPct;
    private int batteryTemp;
    private boolean batteryOverheated = false;

	/**
	 * Called each time a new image arrives in the data stream.
	 */
	@Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

        // record the (approximate) acquisition time
        // FIXME: can we do better than this, perhaps at Camera API level?
        AcquisitionTime acq_time = new AcquisitionTime();

        try {
            // get a reference to the current xb, so it doesn't change from underneath us
            ExposureBlock xb = xbManager.getCurrentExposureBlock();

            // pack the image bytes along with other event info into a RawCameraFrame object
            RawCameraFrame frame = new RawCameraFrame(bytes, acq_time, camera, previewSize);
            frame.setLocation(CFApplication.getLastKnownLocation());
            frame.setOrientation(orientation);
            frame.setBatteryTemp(batteryTemp);

            // use RenderScript for every other frame
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && false) {
                frame.useRenderScript(mRS, mScript, mType);
            }

            // try to assign the frame to the current XB
            boolean assigned = xb.assignFrame(frame);

            // track the acquisition times for FPS calculation
            synchronized(frame_times) {
                frame_times.add_value(acq_time.Nano);
            }
            // update the FPS and Calibration calculation periodically
            if (mL1Processor.mL1Count % fps_update_interval == 0 && !CONFIG.getTriggerLock()
                    && xb.daq_state == CFApplication.State.DATA) {
                updateCalibration();
            }

            // if we failed to assign the block to the XB, just drop it.
            if (!assigned) {
                // FIXME: can we maybe do something smarter here, like try to get the next XB?
                CFLog.e("Cannot assign frame to current XB! Dropping frame.");
                frame.retire();
                return;
            }

            // If we made it here, we can submit the XB to the L1Processor.
            // It will pop the assigned frame from the XB's internal list, and will also handle
            // recycling the buffers.
            frame.setExposureBlock(xb);
            mL1Processor.submitFrame(frame);

        } catch (Exception e) {
            // don't crash
        }
    }

    private double updateFPS() {
        long now = System.nanoTime() - CFApplication.getStartTimeNano();
        if (frame_times != null) {
          synchronized(frame_times) {
                int nframes = frame_times.size();
                if (nframes>0) {
                    last_fps = ((double) nframes) / (now - frame_times.getOldest()) * 1000000000L;
                }
                return last_fps;
            }
        }
        return 0.0;
    }

    private void updateCalibration() {
        int new_l1 = calculateL1Threshold();
        int new_l2 = new_l1 - 1;
        if (new_l2 < 2) {
            new_l2 = 2;
        }

        if (new_l1 != CONFIG.getL1Threshold()) {
            // the L1 threshold is drifting! set the new threshold and trigger a new XB.
            CFLog.i("Now resetting thresholds, L1=" + new_l1 + ", L2=" + new_l2);
            CONFIG.setL1Threshold(new_l1);
            CONFIG.setL2Threshold(new_l2);

            CFLog.i("Triggering new XB.");
            //xbManager.newExposureBlock();
            xbManager.abortExposureBlock();
        }
    }

    public double getFPS() {
        return last_fps;
    }

	// ///////////////////////////////////////


    @Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (gravSensor !=null && event.sensor.getType() == gravSensor.getType()) {
			// get the gravity vector:
			gravity[0] = event.values[0];
			gravity[1] = event.values[1];
			gravity[2] = event.values[2];
		}
		if (magSensor != null && event.sensor.getType() == magSensor.getType()) {
			geomagnetic[0] = event.values[0];
			geomagnetic[1] = event.values[1];
			geomagnetic[2] = event.values[2];
		}
		
		// now update the orientation vector
		float[] R = new float[9];
		boolean succ = SensorManager.getRotationMatrix(R, null, gravity, geomagnetic);
		if (succ) {
			SensorManager.getOrientation(R, orientation);
		} else {
			orientation[0] = 0;
			orientation[1] = 0;
			orientation[2] = 0;
		}
	}

    private final BroadcastReceiver STATE_CHANGE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final CFApplication.State previous = (CFApplication.State) intent.getSerializableExtra(CFApplication.STATE_CHANGE_PREVIOUS);
            final CFApplication.State current = (CFApplication.State) intent.getSerializableExtra(CFApplication.STATE_CHANGE_NEW);
            CFLog.d(DAQActivity.class.getSimpleName() + " state transition: " + previous + " -> " + current);

            if (current != previous) {
                if (current == CFApplication.State.DATA) {
                    doStateTransitionData(previous);
                } else if (current == CFApplication.State.STABILIZATION) {
                    doStateTransitionStabilization(previous);
                } else if (current == CFApplication.State.IDLE) {
                    doStateTransitionIdle(previous);
                } else if (current == CFApplication.State.CALIBRATION) {
                    doStateTransitionCalibration(previous);
                } else if (current == CFApplication.State.RECONFIGURE) {
                    doStateTransitionReconfigure(previous);
                }
            }
        }
    };

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

                if (System.currentTimeMillis() - last_battery_check_time > battery_check_wait )
                {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = context.registerReceiver(null, ifilter);

                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    batteryPct = level / (float)scale;
                    // if overheated, see if battery temp is still falling
                    int newTemp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                    xbManager.updateBatteryTemp(newTemp);
                    CFLog.d("Temperature change: " + batteryTemp + "->" + newTemp);
                    if (batteryOverheated) {
                        // see if temp has stabilized below overheat threshold or has reached a sufficiently low temp
                        batteryOverheated = (newTemp <= batteryTemp && newTemp > batteryStartTemp) || newTemp > batteryOverheatTemp;
                        DataCollectionFragment.getInstance().updateIdleStatus("Cooling battery: " + String.format("%1.1f", newTemp/10.) + "C");
                    } else {
                        // if not in IDLE mode for overheat, must be for low battery
                        DataCollectionFragment.getInstance().updateIdleStatus("Low battery: "+(int)(batteryPct*100)+"%/"+(int)(battery_start_threshold*100)+ "%");
                    }
                    batteryTemp = newTemp;
                    last_battery_check_time = System.currentTimeMillis();
                }

                if (application.getApplicationState()!=edu.uci.crayfis.CFApplication.State.IDLE)
                {
                    if(batteryPct < battery_stop_threshold) {
                        CFLog.d(" Battery too low, going to IDLE mode.");
                        DataCollectionFragment.getInstance().updateIdleStatus("Low battery: "+(int)(batteryPct*100)+"%/"+(int)(battery_start_threshold*100)+ "%");
                        application.setApplicationState(CFApplication.State.IDLE);
                    } else if (batteryTemp > batteryOverheatTemp) {
                        CFLog.d(" Battery too hot, going to IDLE mode.");
                        DataCollectionFragment.getInstance().updateIdleStatus("Cooling battery: " + String.format("%1.1f", batteryTemp/10.) + "C");
                        application.setApplicationState(CFApplication.State.IDLE);
                        batteryOverheated = true;
                    }

                }

                if (application.getApplicationState()==edu.uci.crayfis.CFApplication.State.IDLE
                        && batteryPct > battery_start_threshold && !batteryOverheated)
                {
                    CFLog.d(" Battery ok now, returning to run mode.");
                    setUpAndConfigureCamera();

                    application.setApplicationState(CFApplication.State.STABILIZATION);
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

                    if (LayoutData.mLightMeter != null) {
                        LayoutData.updateData();
                    }

                    if (application.getApplicationState() == CFApplication.State.IDLE)
                    {
                        if (LayoutData.mProgressWheel != null) {
                            if (batteryPct < battery_start_threshold) {
                                LayoutData.mProgressWheel.setText("Low battery " + (int) (batteryPct * 100) + "%/" + (int) (battery_start_threshold * 100) + "%");
                            } else {
                                LayoutData.mProgressWheel.setText("Battery overheated " + String.format("%1.1f", batteryTemp/10.) + "C");
                            }

                            LayoutData.mProgressWheel.setTextColor(Color.WHITE);
                            LayoutData.mProgressWheel.setBarColor(Color.LTGRAY);

                            int progress = (int) (360 * batteryPct);
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
                                    .setTotalPixels((long)mL1Processor.mL1CountData * previewSize.height * previewSize.width)
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

                    String server_address = context.getString(R.string.server_address);
                    String server_port = context.getString(R.string.server_port);
                    String upload_uri = context.getString(R.string.upload_uri);
                    boolean force_https = context.getResources().getBoolean(R.bool.force_https);
                    String upload_proto;
                    if (force_https) {
                        upload_proto = "https://";
                    } else {
                        upload_proto = "http://";
                    }
                    String upload_url = upload_proto + server_address+":"+server_port+upload_uri;

                    updateFPS();

                    if (mLayoutDeveloper != null) {
                        if (mLayoutDeveloper.mAppBuildView != null)
                            mLayoutDeveloper.mAppBuildView.setAppBuild(application.getBuildInformation());

                        final Camera.Size cameraSize = CFApplication.getCameraSize();

                        if (mLayoutDeveloper.mTextView != null) {
                            String devtxt = "@@ Developer View @@\n"
                                    + "State: " + application.getApplicationState() + "\n"
                                    + "L2 trig: " + CONFIG.getL2Trigger() + "\n"
                                    + "total frames - L1: " + mL1Processor.mL1Count + " (L2: " + mL2Processor.mL2Count + ")\n"
                                    + "L1 Threshold:" + (CONFIG != null ? CONFIG.getL1Threshold() : -1) + (CONFIG.getTriggerLock() ? "*" : "") + "\n"
                                    + "fps="+String.format("%1.2f",last_fps)+" target eff="+String.format("%1.2f",target_L1_eff)+"\n"
                                    + "Exposure Blocks:" + (xbManager != null ? xbManager.getTotalXBs() : -1) + "\n"
                                    + "Battery power pct = "+(int)(100*batteryPct)+"%, temp = "
                                    + String.format("%1.1f", batteryTemp/10.) + "C from "+((System.currentTimeMillis()-last_battery_check_time)/1000)+"s ago.\n"
                                    + "\n";

                            if (cameraSize != null) {
                                ResolutionSpec targetRes = CONFIG.getTargetResolution();
                                devtxt += "Image dimensions = " + cameraSize.width + "x" + cameraSize.height + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) +")\n";
                            }
                            ExposureBlock xb = xbManager.getCurrentExposureBlock();
                            devtxt += "xb avg: " + String.format("%1.4f",xb.getPixAverage()) + " max: " + String.format("%1.2f",xb.getPixMax()) + "\n";
                            devtxt += "L1 hist = "+L1cal.getHistogram().toString()+"\n"
                                    + "Upload server = " + upload_url + "\n"
                                    + (mLastLocation != null ? "Current google location: (long=" + mLastLocation.getLongitude() + ", lat=" + mLastLocation.getLatitude() + ") accuracy = " + mLastLocation.getAccuracy() + " provider = " + mLastLocation.getProvider() + " time=" + mLastLocation.getTime() : "") + "\n"
                                    + (mLastLocationDeprecated != null ? "Current android location: (long=" + mLastLocationDeprecated.getLongitude() + ", lat=" + mLastLocationDeprecated.getLatitude() + ") accuracy = " + mLastLocationDeprecated.getAccuracy() + " provider = " + mLastLocationDeprecated.getProvider() + " time=" + mLastLocationDeprecated.getTime() : "") + "\n"
                                    + (CFApplication.getLastKnownLocation() != null ? " Official location = (long="+CFApplication.getLastKnownLocation().getLongitude()+" lat="+CFApplication.getLastKnownLocation().getLatitude() : "") + "\n";
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
