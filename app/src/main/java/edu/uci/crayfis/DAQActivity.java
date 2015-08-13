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

import edu.uci.crayfis.particle.ParticleReco.RecoEvent;

import android.os.Build;
import android.provider.Settings;

import android.view.Window;

import android.view.WindowManager;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Html;
import android.support.v4.view.ViewPager;

import java.lang.OutOfMemoryError;

import android.view.View;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import edu.uci.crayfis.calibration.FrameHistory;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.camera.CameraPreviewView;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exception.IllegalFsmStateException;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.exposure.ExposureBlockManager;
import edu.uci.crayfis.particle.ParticleReco;
import edu.uci.crayfis.server.ServerCommand;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.server.UploadExposureTask;
import edu.uci.crayfis.util.CFLog;
import edu.uci.crayfis.widget.DataView;
import edu.uci.crayfis.widget.MessageView;
import edu.uci.crayfis.widget.StatusView;

import edu.uci.crayfis.LayoutLevels;

//import android.location.LocationListener;


/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends ActionBarActivity implements Camera.PreviewCallback, SensorEventListener,
        ConnectionCallbacks, OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private ViewPager _mViewPager;
    private ViewPagerAdapter _adapter;

    private PagerTabStrip strip;

    private static LayoutBlack mLayoutBlack = LayoutBlack.getInstance();
    private static LayoutData mLayoutData = LayoutData.getInstance();
    private static LayoutHist mLayoutHist = LayoutHist.getInstance();
    private static LayoutTime mLayoutTime = LayoutTime.getInstance();
    private static LayoutDeveloper mLayoutDeveloper = LayoutDeveloper.getInstance();

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication.AppBuild mAppBuild;

    // camera and display objects
	private Camera mCamera;
	private CameraPreviewView mPreview;


    private Timer mUiUpdateTimer;

    // ----8<--------------



	// WakeLock to prevent the phone from sleeping during DAQ
	private PowerManager.WakeLock wl;

	private char[][] histo_chars = new char[256][256];
	// draw some X axis labels
	char[] labels = new char[256];
	// settings

	public static final int targetPreviewWidth = 320;
	public static final int targetPreviewHeight = 240;

	DataProtos.RunConfig run_config = null;

	// to keep track of height/width
	private Camera.Size previewSize;

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
	private int L2busy = 0;

	private ExposureBlockManager xbManager;

    private L1Calibrator L1cal;
	private long L1counter = 0;
    private long L1counter_data = 0;

    private FrameHistory<Long> frame_times;

	private long calibration_start;

	// counter for stabilization mode
	private int stabilization_counter;

    // counter for calibration mode
    private int calibration_counter;

	// L1 hit threshold
	long starttime;

    // number of frames to wait between fps updates
	public static final int fps_update_interval = 20;

    // store value of most recently calculated FPS
    private double last_fps = 0;

	// Thread where image data is processed for L2
	private L2Processor l2thread;

    // Thread for NTP updates
    private SntpUpdateThread ntpThread;

	// class to find particles in frames
	private ParticleReco mParticleReco;

	Context context;

	public void clickedSettings() {

		Intent i = new Intent(this, UserSettingActivity.class);
		startActivity(i);
	}





	// received message when battery is low -- should end run
	public class BatteryLowReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			DAQActivity.this.onPause();
			DAQActivity.this.finish();
		}

	}

    // FIXME This is coupled with OutputManager, might not be needed now that it's going through CFConfig, investigate later.
 	public void updateSettings(JSONObject json) {
 		if (json == null) {
 			return;
 		}

        try {
            final ServerCommand serverCommand = new Gson().fromJson(json.toString(), ServerCommand.class);
            CONFIG.updateFromServer(serverCommand);
            ((CFApplication) getApplication()).savePreferences();

            if (serverCommand.shouldRestartEBManager()) {
                xbManager.newExposureBlock();
            }

            if (serverCommand.shouldRecalibrate()) {
                CFLog.i("DAQActivity SERVER commands us to recalibrate.");
                ((CFApplication) getApplication()).setApplicationState(CFApplication.State.STABILIZATION);
            }
        } catch (JsonSyntaxException e) {
            CFLog.e("Failed to parse server response: " + e.getMessage());
        }
	}

    private static boolean locationWarningGiven = false;

    public void locationWarning() {
        if (!locationWarningGiven) {
            final TextView tx1 = new TextView(this);
            tx1.setText(getResources().getString(R.string.location_warning));
            tx1.setTextColor(Color.WHITE);
            tx1.setBackgroundColor(Color.BLACK);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.location_warn_title)).setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    })

                    .setView(tx1).show();
            locationWarningGiven = true;
        }
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
        previous_item = _mViewPager.getCurrentItem()+1; // FIXME: why do we need this +1?
        //CFLog.d(" Now g to INACTIVE pane due to user inactivity and dimming and invisibling.");
        _mViewPager.setCurrentItem(ViewPagerAdapter.INACTIVE);
        getSupportActionBar().hide();

        try {
            screen_brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
           // CFLog.d(" saving screen brightness of "+screen_brightness);
        } catch (Exception e) { CFLog.d(" Unable to find screen brightness"); screen_brightness=200;}
        Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS, 0);
        findViewById(R.id.camera_preview).setVisibility(View.INVISIBLE);

        strip.setTextColor(Color.BLACK);
        strip.setTabIndicatorColor(Color.BLACK);
        strip.setBackgroundColor(Color.BLACK);


        sleep_mode=true;
        sleeping_since = System.currentTimeMillis();
        cands_before_sleeping = l2thread.getTotalEvents();
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

        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.STATUS)
  		  tx1.setText(getResources().getString(R.string.crayfis_about)+"\n"
                  +getResources().getString(R.string.help_data)+"\n\n"+
                  getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
				+ s);
        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.DATA)
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                            "\n"+getResources().getString(R.string.help_hist)+"\n\n"+
                            getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s
                           );
        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.DOSIMETER)
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_dosimeter)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)

                    + s);

        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.GALLERY)
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_gallery)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);

        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.LOGIN)
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_login)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);

        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.LEADER)
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_leader)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);

        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.DEVELOPER)
        tx1.setText(getResources().getString(R.string.crayfis_about)+
                "\n"+getResources().getString(R.string.toast_devel)+"\n"+
                getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                + s);

        if (_mViewPager.getCurrentItem()==ViewPagerAdapter.INACTIVE)
            tx1.setText(getResources().getString(R.string.crayfis_about)+
                    "\n"+getResources().getString(R.string.toast_black)+"\n\n"+
                    getResources().getString(R.string.swipe_help)+"\n"+getResources().getString(R.string.more_details)
                    + s);

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
                        // switch to feedback item
                        _mViewPager.setCurrentItem(ViewPagerAdapter.FEEDBACK);
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
                mParticleReco.reset();
                L1cal.clear();
                synchronized(frame_times) {
                    frame_times.clear();
                }
                calibration_start = System.currentTimeMillis();
                xbManager.newExposureBlock();
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + ((CFApplication) getApplication()).getApplicationState());
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
            case INIT:
                l2thread.setFixedThreshold(true);
                xbManager.newExposureBlock();

                break;
            case CALIBRATION:
                //long calibration_time = l2thread.getCalibrationStop() - calibration_start;
                //int target_events = (int) (CONFIG.getTargetEventsPerMinute() * calibration_time * 1e-3 / 60.0);

                //CFLog.i("Calibration: Processed " + mParticleReco.event_count + " frames in " + (int) (calibration_time * 1e-3) + " s; target events = " + target_events);


                int new_thresh = calculateL1Threshold();
                // build the calibration result object
                DataProtos.CalibrationResult.Builder cal = DataProtos.CalibrationResult.newBuilder();
                /*
                // (ugh, why can't primitive arrays be Iterable??)
                for (double v : mParticleReco.h_pixel) {
                    cal.addHistPixel((int) v);
                }
                for (double v : mParticleReco.h_l2pixel) {
                    cal.addHistL2Pixel((int) v);
                }
                */
                for (int v : L1cal.getHistogram()) {
                    cal.addHistMaxpixel(v);
                }
                /*
                for (double v : mParticleReco.h_numpixel) {
                    cal.addHistNumpixel((int) v);
                }
                */
                // and commit it to the output stream
                CFLog.i("DAQActivity Committing new calibration result.");
                UploadExposureService.submitCalibrationResult(this, cal.build());

                // update the thresholds
                //new_thresh = mParticleReco.calculateThresholdByEvents(target_events);
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
                xbManager.newExposureBlock();

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
                // This is the first state transisiton of the app. Go straight into stabilization
                // so the calibratoin will be clean.
                if (l2thread != null)
                {
                l2thread.setFixedThreshold(false);
                l2thread.clearQueue();
                }
                stabilization_counter = 0;
                calibration_counter = 0;
                xbManager.newExposureBlock();
                break;

            // coming out of calibration and data should be the same.
            case CALIBRATION:
            case DATA:
                l2thread.clearQueue();
                stabilization_counter = 0;
                calibration_counter = 0;
                xbManager.abortExposureBlock();
                break;
            case STABILIZATION:
                // just reset the counter.
                stabilization_counter = 0;
                calibration_counter = 0;

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
		switch(previousState) {
            case CALIBRATION:
            case STABILIZATION:
                // for calibration or stabilization, mark the block as aborted
                xbManager.abortExposureBlock();
                break;
            case DATA:
                // for data, just close out the current XB
                xbManager.newExposureBlock();
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
        b.setCameraParams(mCamera.getParameters().flatten());




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

        /*
        TODO: Flush the UploadExposureService queue.

        The idea here is that if there are files left in the queue, whenever this activity is
        relaunched, that would be a good time to try to flush it.  This avoids background
        watchers and makes it more probible that the data is uploaded at a time that is
        convenient to the user.
         */

        mAppBuild = ((CFApplication) getApplication()).getBuildInformation();

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
                CFLog.i("Uploading file: " + files[i].getName());
                new UploadExposureTask((CFApplication) getApplication(),
                        new UploadExposureService.ServerInfo(this), files[i]).execute();
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



        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);


        setContentView(R.layout.video);




        _mViewPager = (ViewPager) findViewById(R.id.viewPager);
        _adapter = new ViewPagerAdapter(getApplicationContext(),getSupportFragmentManager());
        _mViewPager.setAdapter(_adapter);
        _mViewPager.setCurrentItem(ViewPagerAdapter.STATUS);




		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreviewView(this, this, true);

        CFLog.d("DAQActivity: Camera preview view is "+mPreview);

		context = getApplicationContext();

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);




        L1counter = 0;
        L1counter_data = 0;

        if (L1cal == null) {
            L1cal = L1Calibrator.getInstance();
        }
        if (frame_times == null) {
            frame_times = new FrameHistory<Long>(100);
        }

		starttime = System.currentTimeMillis();
        last_user_interaction = starttime;
        /////////
        newLocation(new Location("BLANK"),false);
        buildGoogleApiClient(); // connect() and disconnect() called in onStart() and onStop()

        // backup location if Google play isn't working or installed
        mLastLocationDeprecated = getLocationDeprecated();
        newLocation(mLastLocationDeprecated,true);

		xbManager = ExposureBlockManager.getInstance(this);

        LocalBroadcastManager.getInstance(this).registerReceiver(STATE_CHANGE_RECEIVER,
                new IntentFilter(CFApplication.ACTION_STATE_CHANGE));

        strip = PagerTabStrip.class.cast(findViewById(R.id.pts_main));
        strip.setDrawFullUnderline(false);
        strip.setTabIndicatorColor(Color.RED);
        strip.setBackgroundColor(Color.WHITE);
        strip.setNonPrimaryAlpha(0.5f);
        strip.setTextSpacing(25);
        strip.setTextColor(Color.BLACK);
        strip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);


      


	}

    @Override
    protected void onStart()
    {
        super.onStart();
        CFLog.d("CALL: onStart!");
        mGoogleApiClient.connect();
        CFLog.d("DAQActivity onStart: wake lock held?"+wl.isHeld());


    }
    @Override
    protected void onStop()
    {
        super.onStop();
        CFLog.d("DAQActivity onStop: wake lock held?"+wl.isHeld());

        if (wl.isHeld())
            wl.release();

        mGoogleApiClient.disconnect();

        ///  Moved from onPause

        CFLog.i("DAQActivity Suspending!");

        // stop the camera preview and all processing
        if (mCamera != null) {
            mPreview.setCamera(null);
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;

            // clear out any (old) buffers
            l2thread.clearQueue();

            // If the app is being paused, we don't want that
            // counting towards the current exposure block.
            // So close it out cleaning by moving to the IDLE state.
            // FIXME This may not be valid if things are running in the background.
            ((CFApplication) getApplication()).setApplicationState(CFApplication.State.IDLE);
        }
        // give back brightness control
        Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,screen_brightness_mode);


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
		l2thread.stopThread();
		l2thread = null;

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
		mSensorManager.registerListener(this, sens, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);

		if (mCamera != null)
			throw new RuntimeException(
					"Bug, camera should not be initialized already");

        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.STABILIZATION);

		// configure the camera parameters and start it acquiring frames.
		setUpAndConfigureCamera();

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

        CFLog.d("DAQActivity onPause: wake lock held?"+wl.isHeld());


		mSensorManager.unregisterListener(this);


		CFLog.i("DAQActivity Suspending!");

		// stop the camera preview and all processing
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;

			// clear out any (old) buffers
			l2thread.clearQueue();

			// If the app is being paused, we don't want that
			// counting towards the current exposure block.
			// So close it out cleaning by moving to the IDLE state.
            // FIXME This may not be valid if things are running in the background.
            ((CFApplication) getApplication()).setApplicationState(CFApplication.State.IDLE);
		}

	}

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.daq_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
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
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            userErrorMessage(getResources().getString(R.string.camera_error),true);

        }
        try {

            Camera.Parameters param= mCamera.getParameters();
                CFLog.d("params: Camera params are" + param.flatten());

            // Select the preview size closest to 320x240
            // Smaller images are recommended because some computer vision
            // operations are very expensive
            List<Camera.Size> sizes = param.getSupportedPreviewSizes();
            // previewSize = sizes.get(closest(sizes,640,480));
            previewSize = sizes.get(closest(sizes, targetPreviewWidth, targetPreviewHeight));
            // Camera.Size previewSize = sizes.get(closest(sizes,176,144));
            CFLog.d(" preview size="+previewSize);

            if (mParticleReco == null) {
                // if we don't already have a particleReco object setup,
                // do that now that we know the camera size.
                mParticleReco = ParticleReco.getInstance();
                mParticleReco.setPreviewSize(previewSize);
                mLayoutBlack.previewSize = previewSize;
                l2thread = new L2Processor(this, previewSize);
                l2thread.start();

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

                mCamera.setParameters(param);
            }
            catch (RuntimeException ex) {
                // but some phones will throw a fit. So just give it a "supported" range.
                CFLog.w("DAQActivity Unable to set maximum frame rate. Falling back to default range.");
                param.setPreviewFpsRange(minfps, maxfps);

                mCamera.setParameters(param);
            }

            // Create an instance of Camera
            CFLog.d("before SetupCamera mpreview = "+mPreview+" mCamera="+mCamera);

            } catch (Exception e) {
                userErrorMessage(getResources().getString(R.string.camera_error),true);

            }



        try {
        mPreview.setCamera(mCamera);
          }  catch (Exception e) {
           userErrorMessage(getResources().getString(R.string.camera_error),true);
           }
        CFLog.d("after SetupCamera mpreview = "+mPreview+" mCamera="+mCamera);
	}

	/**
	 * Goes through the size list and selects the one which is the closest
	 * specified size
	 */
	public static int closest(List<Camera.Size> sizes, int width, int height) {
		int best = -1;
		int bestScore = Integer.MAX_VALUE;

		for (int i = 0; i < sizes.size(); i++) {
			Camera.Size s = sizes.get(i);

			CFLog.d("setup: size is w=" + s.width + " x " + s.height);
			int dx = s.width - width;
			int dy = s.height - height;

			int score = dx * dx + dy * dy;
			if (score < bestScore) {
				best = i;
				bestScore = score;
			}
		}

		return best;
	}

    private long last_user_interaction=0;
    private int previous_item = ViewPagerAdapter.STATUS;
    private boolean sleep_mode = false;
    private int screen_brightness = 0;
    @Override
    public void onUserInteraction()
    {
        last_user_interaction=System.currentTimeMillis();
       // CFLog.d(" The UserActivity at time= "+last_user_interaction);
        if (sleep_mode==true)
        {
            //wake up
            getSupportActionBar().show();

            // if we somehow didn't capture the old brightness, don't set it to zero
            if (screen_brightness<=150) screen_brightness=150;
          //  CFLog.d(" Switching out of INACTIVE pane to pane "+previous_item+" and setting brightness to "+screen_brightness);

            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS, screen_brightness);

            findViewById(R.id.camera_preview).setVisibility(View.VISIBLE);
            _mViewPager.setCurrentItem(previous_item);
            strip.setTextColor(Color.BLACK);
            strip.setBackgroundColor(Color.WHITE);
            strip.setTabIndicatorColor(Color.RED);
            sleep_mode=false;

            long current_time = System.currentTimeMillis();
            float time_sleeping = (current_time-sleeping_since)*(float)1e-3;
            int cand_sleeping = l2thread.getTotalEvents()-cands_before_sleeping;
            if (time_sleeping > 5.0)
            {
                Toast.makeText(this, "Your device saw "+cand_sleeping+" particle candidates in the last "+ String.format("%1.1f",time_sleeping)+"s",Toast.LENGTH_LONG).show();


            }

        }
    }


    private boolean fix_threshold=false;

    private SntpClient mSntpClient = null;

	/**
	 * Called each time a new image arrives in the data stream.
	 */
	@Override
	public void onPreviewFrame(byte[] bytes, Camera camera) {
		// NB: since we are using NV21 format, we will be discarding some bytes at
		// the end of the input array (since we only need to grayscale output)

        // record the (approximate) acquisition time
        // FIXME: can we do better than this, perhaps at Camera API level?
        long acq_time_nano = System.nanoTime() - CFApplication.getStartTimeNano();
        long acq_time = System.currentTimeMillis();
        long acq_time_ntp = mSntpClient.getNtpTime();
        long diff = acq_time - acq_time_ntp;

        //CFLog.d(" Frame times millis = "+acq_time+ " ntp = "+acq_time_ntp+" diff = "+diff);

        // sanity check
        if (bytes == null) return;

        try {
            // get a reference to the current xb, so it doesn't change from underneath us
            ExposureBlock xb = xbManager.getCurrentExposureBlock();

            // next, bump the number of L1 events seen by this xb.
            L1counter++;
            xb.L1_processed++;

            // pack the image bytes along with other event info into a RawCameraFrame object
            RawCameraFrame frame = new RawCameraFrame(bytes, acq_time, acq_time_nano,acq_time_ntp , xb, orientation, camera.getParameters().getPreviewSize());
            frame.setLocation(CFApplication.getLastKnownLocation());

            // show the frame to the L1 calibrator
            L1cal.AddFrame(frame);
            // and track the acquisition times for FPS calculation
            synchronized(frame_times) {
                frame_times.add_value(acq_time);
            }
            // update the FPS calculation periodically
            if (L1counter % fps_update_interval == 0) {
                double fps = updateFPS();
                //CFLog.d("DAQActivity new fps = "+fps+" at time = "+acq_time);

            }

            final CFApplication application = (CFApplication) getApplication();
            if (application.getApplicationState() == CFApplication.State.CALIBRATION) {
                // if we are in (L1) calibration mode, there's no need to do anything else with this
                // frame; the L1 calibrator already saw it. Just check to see if we're done calibrating.
                calibration_counter++;

                if (calibration_counter > CONFIG.getCalibrationSampleFrames()) {
                    application.setApplicationState(CFApplication.State.DATA);
                }
                return;

            /*
			// In calbration mode, there's no need for L1 trigger; just go straight to L2
			boolean queue_accept = l2thread.submitToQueue(frame);

			if (! queue_accept) {
				// oops! the queue is full... this frame will be dropped.
				CFLog.e("DAQActivity Could not add frame to L2 Queue!");
				L2busy++;
			}

			return;
			*/
            }

            if (application.getApplicationState() == CFApplication.State.STABILIZATION) {
                // If we're in stabilization mode, just drop frames until we've skipped enough
                stabilization_counter++;
                if (stabilization_counter > CONFIG.getStabilizationSampleFrames()) {
                    application.setApplicationState(CFApplication.State.CALIBRATION);
                }
                return;
            }

            if (application.getApplicationState() == CFApplication.State.IDLE) {
                // Not sure why we're still acquiring frames in IDLE mode...
                CFLog.w("DAQActivity Frames still being recieved in IDLE mode");
                return;
            }

            L1counter_data++;


            // periodically check if the L1 calibration has drifted
            if (L1counter % fps_update_interval == 0 && !fix_threshold) {
                int new_l1 = calculateL1Threshold();
                int new_l2 = new_l1 - 1;
                if (new_l2 < 2) {
                    new_l2 = 2;
                }

                if (new_l1 != CONFIG.getL1Threshold()) {
                    // the L1 threshold is drifting! set the new threshold and trigger a new XB.
                    CONFIG.setL1Threshold(new_l1);
                    CONFIG.setL2Threshold(new_l2);
                    xbManager.newExposureBlock();
                    CFLog.i("Now resetting thresholds, L1=" + new_l1 + ", L2=" + new_l2);
                    CFLog.i("Triggering new XB.");


                }
            }

            // prescale
            // Jodi - removed L1prescale as it never changed.
            if (L1counter % 1 == 0) {
                // make sure there's room on the queue
                if (l2thread.getRemainingCapacity() > 0) {
                    // check if we pass the L1 threshold
                    boolean pass = false;
                /*
				int length = previewSize.width * previewSize.height;
                int max=-1;
				for (int i = 0; i < length; i++) {
					// make sure we promote the (signed) byte to int for comparison!
                    if ( (bytes[i] & 0xFF) > max) { max = (bytes[i] & 0xFF); }
                    if ( (bytes[i] & 0xFF) > CONFIG.getL1Threshold()) {
						// Okay, found a pixel above threshold. No need to continue
						// looping.
						pass = true;
						break;
					}
				}
				*/
                    int max = frame.getPixMax();
                    if (max > xb.L1_thresh) {
                        // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
                        // have changed.
                        pass = true;
                    }
                    if (pass) {
                        xb.L1_pass++;

                        // this frame has passed the L1 threshold, put it on the
                        // L2 processing queue.
                        boolean queue_accept = l2thread.submitToQueue(frame);

                        if (!queue_accept) {
                            // oops! the queue is full... this frame will be dropped.
                            CFLog.e("DAQActivity Could not add frame to L2 Queue!");
                            L2busy++;
                        }
                    }
                } else {
                    // no room on the L2 queue! We'll have to skip this frame.
                    xb.L1_skip++;
                }

            }


            // Can only do trivial amounts of image processing inside this function
            // or else bad stuff happens.
            // To work around this issue most of the processing has been pushed onto
            // a thread and the call below
            // tells the thread to wake up and process another image

        } catch (Exception e)
        { // dont' crash, jsut drop frame
        }
	}

    private double updateFPS() {
        long now = System.currentTimeMillis();
        if (frame_times != null) {
          synchronized(frame_times) {
                int nframes = frame_times.size();
                last_fps = ((double) nframes) / (now - frame_times.getOldest()) * 1000;
                return last_fps;
            }
        }
        return 0.0;
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

                if (! ((CFApplication) getApplicationContext()).isNetworkAvailable()) {
                    if (LayoutData.mMessageView != null)
                        LayoutData.mMessageView.setMessage(MessageView.Level.ERROR, "Error: "+getResources().getString(R.string.network_unavailable));
                } else if (!UploadExposureTask.sPermitUpload.get()) {
                    if (LayoutData.mMessageView != null)
                        LayoutData.mMessageView.setMessage(MessageView.Level.WARNING, getResources().getString(R.string.server_overload));
                } else if (!UploadExposureTask.sValidId.get()) {
                    if (LayoutData.mMessageView != null)
                        LayoutData.mMessageView.setMessage(MessageView.Level.WARNING, getResources().getString(R.string.bad_user_code));
                } else if (L2busy > 0) {
                    final String ignoredFrames = getResources().getQuantityString(R.plurals.total_frames, L2busy, L2busy);
                    if (LayoutData.mMessageView != null )
                        LayoutData.mMessageView.setMessage(MessageView.Level.WARNING, getResources().getString(R.string.ignored)+" " + ignoredFrames);
                } else {
                    if (LayoutData.mMessageView != null )

                        LayoutData.mMessageView.setMessage(null, null);
                }

                final CFApplication application = (CFApplication) getApplication();


                //CFLog.d(" The last recorded user interaction was at "+((last_user_interaction - System.currentTimeMillis())/1e3)+" sec ago");
                if ( sleep_mode == false
                        && (last_user_interaction - System.currentTimeMillis()) < -30e3
                        && (application.getApplicationState() == CFApplication.State.DATA)
                        ) // wait 10s after going into DATA mode
                {
                    goToSleep();

                }

                // turn on developer options if it has been selected
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (_adapter != null)
                    _adapter.setDeveloperMode(sharedPrefs.getBoolean("prefEnableGallery", false));
                l2thread.save_images = sharedPrefs.getBoolean("prefEnableGallery", false);
                // fix_threshold = sharedPrefs.getBoolean("prefFixThreshold", false); // expert only

                try {

                    if (LayoutData.mLightMeter != null) {
                        LayoutData.updateData();
                    }

                    if (LayoutLevels.mStatusView != null)
                    {
                        LayoutLevels.mStatusView.setStatus((int) (1.0e-3 * xbManager.getExposureTime()),
                                (int) (getFPS()));

                    }




                    if (application.getApplicationState() == CFApplication.State.STABILIZATION)
                    {
                        if (LayoutData.mProgressWheel != null) {
                            LayoutData.mProgressWheel.setText(getResources().getString(R.string.stabilization));
                            LayoutData.mProgressWheel.setTextSize(22);

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
                            LayoutData.mProgressWheel.setTextSize(27);


                            LayoutData.mProgressWheel.setTextColor(Color.RED);
                            LayoutData.mProgressWheel.setBarColor(Color.RED);

                            int needev = CONFIG.getCalibrationSampleFrames();
                            float frac = calibration_counter / ((float) 1.0 * needev);
                            int progress = (int) (360 * frac);
                            LayoutData.mProgressWheel.setProgress(progress);
                            LayoutData.mProgressWheel.stopGrowing();
                            LayoutData.mProgressWheel.showBackground();


                        }
                    }
                    if (application.getApplicationState() == CFApplication.State.DATA)
                    {
                        if (LayoutData.mProgressWheel != null) {

                            LayoutData.mProgressWheel.setTextSize(30);


                            LayoutData.mProgressWheel.setText(getResources().getString(R.string.taking_data));
                            LayoutData.mProgressWheel.setTextColor(0xFF00AA00);
                            LayoutData.mProgressWheel.setBarColor(0xFF00AA00);

                            // solid circle
                            LayoutData.mProgressWheel.setProgress(360);
                            LayoutData.mProgressWheel.showBackground();
                            LayoutData.mProgressWheel.grow();

                        }


                        if (mParticleReco != null) {
                            final DataView.Status dstatus = new DataView.Status.Builder()
                                    .setTotalEvents((int) mParticleReco.h_l2pixel.getIntegral())
                                    .setTotalPixels(L1counter_data * previewSize.height * previewSize.width)
                                    .setTotalFrames(L1counter_data)
                                    .build();


                            if (LayoutData.mDataView != null) {
                                LayoutData.mDataView.setStatus(dstatus);
                            }
                        }
                        boolean show_splashes = sharedPrefs.getBoolean("prefSplashView", true);
                        if (show_splashes && mLayoutBlack != null) {
                            try {
                                RecoEvent ev = l2thread.display_pixels.poll(10, TimeUnit.MILLISECONDS);
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

                    if (mLayoutDeveloper != null) {
                        if (mLayoutDeveloper.mAppBuildView != null)
                            mLayoutDeveloper.mAppBuildView.setAppBuild(((CFApplication) getApplication()).getBuildInformation());
                        if (mLayoutDeveloper.mTextView != null)
                            mLayoutDeveloper.mTextView.setText("@@ Developer View @@\n L1 Threshold:"
                                            + (CONFIG != null ? CONFIG.getL1Threshold() : -1) + "\n" + "fps="+last_fps+" target eff="+target_L1_eff+"\n"
                                            + "Exposure Blocks:" + (xbManager != null ? xbManager.getTotalXBs() : -1) + "\n"
                                            + "Image dimensions = "+previewSize.height+"x"+previewSize.width + "\n"
                                            + "L1 hist = "+L1cal.getHistogram().toString()+"\n"
                                            + "Upload server = " + upload_url + "\n"
                                            + (mLastLocation != null ? "Current google location: (long=" + mLastLocation.getLongitude() + ", lat=" + mLastLocation.getLatitude() + ") accuracy = " + mLastLocation.getAccuracy() + " provider = " + mLastLocation.getProvider() + " time=" + mLastLocation.getTime() : "") + "\n"
                                            + (mLastLocationDeprecated != null ? "Current android location: (long=" + mLastLocationDeprecated.getLongitude() + ", lat=" + mLastLocationDeprecated.getLatitude() + ") accuracy = " + mLastLocationDeprecated.getAccuracy() + " provider = " + mLastLocationDeprecated.getProvider() + " time=" + mLastLocationDeprecated.getTime() : "") + "\n"
                                            + (CFApplication.getLastKnownLocation() != null ? " Official location = (long="+CFApplication.getLastKnownLocation().getLongitude()+" lat="+CFApplication.getLastKnownLocation().getLatitude() : "") + "\n"
                            );
                    }

                    if (location_valid(CFApplication.getLastKnownLocation()) == false)
                    {
                        if (LayoutData.mMessageView != null)
                            LayoutData.mMessageView.setMessage(MessageView.Level.ERROR, LayoutData.mMessageView.getText() + "\n Error: "+
                                    getResources().getString(R.string.location_unavailable));
                        if (application.getApplicationState() == CFApplication.State.DATA
                            && ( System.currentTimeMillis() - last_location_warning > 300e3)) // every 5 mins
                        {
                            locationWarning();
                            last_location_warning = System.currentTimeMillis();
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
    };
}
