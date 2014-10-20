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

import java.lang.Math;

import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uci.crayfis.ParticleReco.RecoEvent;
import edu.uci.crayfis.ParticleReco.RecoPixel;
import edu.uci.crayfis.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;

/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends Activity implements Camera.PreviewCallback, SensorEventListener, OnSharedPreferenceChangeListener {

	public static final String TAG = "DAQActivity";

	// camera and display objects
	private Camera mCamera;
	private Visualization mDraw;
	private CameraPreview mPreview;

	// WakeLock to prevent the phone from sleeping during DAQ
	PowerManager.WakeLock wl;

	private char[][] histo_chars = new char[256][256];
	// draw some X axis labels
	char[] labels = new char[256];
	// settings

	public static final int targetPreviewWidth = 320;
	public static final int targetPreviewHeight = 240;

	// simple class to group a timestamp and byte array together
	public class RawCameraFrame {
		public final byte[] bytes;
		public final long acq_time;
		public final ExposureBlock xb;
		public Location location;
		public float[] orientation;
		
		public RawCameraFrame(byte[] bytes, long t, ExposureBlock xb, float[] orient) {
			this.bytes = bytes;
			this.acq_time = t;
			this.xb = xb;
			this.orientation = orient.clone();
		}
	}
	
	// Maximum number of raw camera frames to allow on the L2Queue
	private static final int L2Queue_maxFrames = 2;
	// Queue for frames to be processed by the L2 thread
	ArrayBlockingQueue<RawCameraFrame> L2Queue = new ArrayBlockingQueue<RawCameraFrame>(L2Queue_maxFrames);
	
	public String device_id;
	public String build_version;
	public int build_version_code;
	public UUID run_id;
	DataProtos.RunConfig run_config = null;
	
	// max amount of time to wait on L2Queue (seconds)
	int L2Timeout = 1;

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

	// Android image data used for displaying the results
	// private Bitmap output;

	// how many events L1 skipped (after prescale)
	private int L1skip = 0;
	// how many events L1 processed (after prescale)
	private int L1proc = 0;

	// how many events L1 skipped (after prescale)
	private int L2skip = 0;
	// how many events L1 processed (after prescale)
	private int L2proc = 0;
	
	// keep track of how often we had to drop a frame at L1
	// because the L2 queue was full.
	private int L2busy = 0;

	// how many particles seen > thresh
	private int totalPixels = 0;
	private int totalEvents = 0;
	private int totalXBs = 0;
	private int committedXBs = 0;
	
	private ExposureBlockManager xbManager;

	private long L1counter = 0;
	private long L2counter = 0;

	private int L1prescale = 1;
	private int L2prescale = 1;

	public enum state {
		INIT, CALIBRATION, DATA, STABILIZATION, IDLE,
	};

	private state current_state;
	private boolean fixed_threshold;
	
	private long calibration_start;
	private long calibration_stop;
	
	// how many frames to sample during calibration
	// (more frames is longer but gives better statistics)
	public final int default_calibration_sample_frames = 1000;
	private int calibration_sample_frames;
	
	// targeted max. number of events per minute to allow
	// (lower rate => higher threshold)
	public final float default_target_events_per_minute = 60;
	private float target_events_per_minute = default_target_events_per_minute;
	
	// the nominal period for an exposure block (in seconds)
	public static final int default_xb_period = 2*60;
	private int xb_period = default_xb_period;
	
	// number of frames to pass during stabilization periods.
	public static final int stabilization_sample_frames = 45;
	// counter for stabilization mode
	private int stabilization_counter;

	// L1 hit threshold
	private int L1thresh = 0;
	private int L2thresh = 5;
	long starttime;
	long lastUploadTime;
	long lastL2time;

	// calibration analysis parameters
	// calibration rate = number of seconds per background photon
	int stat_factor = 5; // run for stat_factor*calibration_rate
							// until we get stat_factor photons
	// a larger stat factor will mean a more accurate threshold but
	// longer calibration time
	int calibration_rate = 10;
	
	// Location stuff
	private Location currentLocation;
	private LocationManager locationManager;
	private LocationListener locationListener;
	
	// number of frames to wait between fps updates
	public static final int fps_update_interval = 30;
	
	private long L1_fps_start = 0;
	private long L1_fps_stop = 0;

	// Thread where image data is processed for L2
	private L2Processor l2thread;
	
	// thread to handle output of committed data
	private OutputManager outputThread;

	// class to find particles in frames
	private ParticleReco reco;

	// Object used for synchronizing output image
	private final Object lockOutput = new Object();

	Context context;

	public void clickedSettings(View view) {

		Intent i = new Intent(this, UserSettingActivity.class);
		startActivity(i);
	}
	
	public void clickedClose(View view) {

		onPause();
		DAQActivity.this.finish();
	}

	// received message when power is disconnected -- should end run
	public class MyDisconnectReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			DAQActivity.this.onPause();
			DAQActivity.this.finish();
		}

	}
	
 	public void updateSettings(JSONObject json) {
 		if (json == null) {
 			return;
 		}
		SharedPreferences localPrefs = getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = localPrefs.edit();
		boolean restart_xb = false;
 		try {
 			if (json.has("set_L1_thresh")) {
 				editor.putInt("L1_thresh", json.getInt("set_L1_thresh"));
 				Log.i(TAG, "GOT L1 from SERVER" + json.getInt("set_L1_thresh"));
 				restart_xb = true;
 			}
 			if (json.has("set_L2_thresh")) {
 				editor.putInt("L2_thresh", json.getInt("set_L2_thresh"));
 				restart_xb = true;
 			}
 			if (json.has("set_target_L2_rate")) {
 				editor.putFloat("target_events_per_minute", (float)json.getDouble("set_target_L2_rate"));
 			}
 			if (json.has("set_calib_len")) {
 				editor.putInt("calibration_sample_frames", json.getInt("set_calib_len"));
 			}
 			if (json.has("set_xb_period")) {
 				editor.putInt("xb_period", json.getInt("set_xb_period"));
 			}
 			if (json.has("set_max_upload_interval")) {
 				editor.putInt("max_upload_interval", json.getInt("set_max_upload_interval"));
 			}
 			if (json.has("set_upload_size_max")) {
 				editor.putInt("max_chunk_size", json.getInt("set_upload_size_max"));
 			}
 			if (json.has("set_cache_upload_interval")) {
 				editor.putInt("min_cache_upload_interval", json.getInt("set_cache_upload_interval"));
 			}
 			if (json.has("set_qual_pix_frac")) {
 				editor.putFloat("qual_pix_frac", (float)json.getDouble("set_qual_pix_frac"));
 				restart_xb = true;
 			}
 			if (json.has("set_qual_bg_avg")) {
 				editor.putFloat("qual_bg_avg", (float)json.getDouble("set_qual_bg_avg"));
 				restart_xb = true;
 			}
 			if (json.has("set_qual_bg_var")) {
 				editor.putFloat("qual_bg_var", (float)json.getDouble("set_qual_bg_var"));
 				restart_xb = true;
 			}
 		} catch (JSONException ex) {
 			Log.e(TAG, "Malformed JSON from server!");
 			return;
 		}
 		// save the changes.
 		editor.apply();
 		
 		// restart the XB if we changed some setting that should invalidate it:
 		if (restart_xb) {
 			xbManager.newExposureBlock();
 		}
 		
 		// finally, if the server commanded us to enter calibration mode, do so now.
 		if (json.has("cmd_recalibrate")) {
 			Log.i(TAG, "SERVER commands us to recalibrate.");
 			toStabilizationMode();
 		}
	}
 	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.i(TAG, "UPDATED SHARED PREF: " + key);
		if (key == "L1_thresh") {
			L1thresh = prefs.getInt(key, 0);
		} else if (key == "L2_thresh") {
			L2thresh = prefs.getInt(key, 0);
		}
		
		// make sure we didn't set the threshold(s) to zero... otherwise something weird is happening
		if (L1thresh == 0 || L2thresh == 0) {
			// okay i have no idea how we got here, but let's force recalibration.
			toStabilizationMode();
		}
		
		if (key == "target_events_per_minute") {
			target_events_per_minute = prefs.getFloat(key, default_target_events_per_minute);
		} else if (key == "calibration_sample_frames") {
			calibration_sample_frames = prefs.getInt(key, default_calibration_sample_frames);
		} else if (key == "xb_period") {
			xb_period = prefs.getInt(key, default_xb_period);
		}
	}
 	
	public void clickedAbout(View view) {

		final SpannableString s = new SpannableString(
				"crayfis.ps.uci.edu/about");

		final TextView tx1 = new TextView(this);
		tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles. More details:  "
				+ s);

		tx1.setAutoLinkMask(RESULT_OK);
		tx1.setMovementMethod(LinkMovementMethod.getInstance());

		Linkify.addLinks(s, Linkify.WEB_URLS);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("About CRAYFIS").setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				})

				.setView(tx1).show();
	}

	private void newLocation(Location location) {
		currentLocation = location;
	}
	
	private void toCalibrationMode() {
		// The *only* valid way to get into calibration mode
		// is after stabilizaton.
		switch (current_state) {
		case STABILIZATION:
			Log.i(TAG, "FSM Transition: STABILIZATION -> CALIBRATION");
			
			// clear histograms and counters before calibration
			reco.reset();
			
			calibration_start = System.currentTimeMillis();
			current_state = DAQActivity.state.CALIBRATION;
			
			// Start a new exposure block
			xbManager.newExposureBlock();
			
			break;
			
		default:
			throw new RuntimeException("Bad FSM transition: " + current_state.toString() + " -> CALIBRATION");
		}
	}
	
	private void toDataMode() {
		switch (current_state) {
		case INIT:
			Log.i(TAG, "FSM Transition: INIT -> DATA");
			fixed_threshold = true;
			current_state = DAQActivity.state.DATA;
			
			// Start a new exposure block
			xbManager.newExposureBlock();
			
			break;
		case CALIBRATION:
			// Okay, we're just finished calibrating!
			
			Log.i(TAG, "FSM Transition: CALIBRATION -> DATA");

			int new_thresh;
			long calibration_time = calibration_stop - calibration_start;
			int target_events = (int) (target_events_per_minute * calibration_time* 1e-3 / 60.0);
						
			Log.i("Calibration", "Processed " + reco.event_count + " frames in " + (int)(calibration_time*1e-3) + " s; target events = " + target_events);
						
			// build the calibration result object
			DataProtos.CalibrationResult.Builder cal = DataProtos.CalibrationResult.newBuilder();
			// (ugh, why can't primitive arrays be Iterable??)
			for (int v : reco.h_pixel.values) {
				cal.addHistPixel(v);
			}
			for (int v : reco.h_l2pixel.values) {
				cal.addHistL2Pixel(v);
			}
			for (int v : reco.h_maxpixel.values) {
				cal.addHistMaxpixel(v);
			}
			for (int v : reco.h_numpixel.values) {
				cal.addHistNumpixel(v);
			}
			// and commit it to the output stream
			Log.i(TAG, "Committing new calibration result.");
			outputThread.commitCalibrationResult(cal.build());
			
			// update the thresholds
			new_thresh = reco.calculateThresholdByEvents(target_events);
			Log.i(TAG, "Setting new L1 threshold: {"+ L1thresh + "} -> {" + new_thresh + "}");
			L1thresh = new_thresh;
			
			// FIXME: we should have a better calibration for L2 threshold.
			// For now, we choose it to be just below L1thresh.
			if (L1thresh > 2) {
				L2thresh = L1thresh - 1;
			}
			else {
				// Okay, if we're getting this low, we shouldn't try to
				// set the L2thresh any lower, else event frames will be huge.
				L2thresh = L1thresh;
			}

			// Finally, set the state and start a new xb
			current_state = DAQActivity.state.DATA;
			xbManager.newExposureBlock();
			
			break;
		default:
			throw new RuntimeException("Bad FSM transition: " + current_state.toString() + " -> DATA");
		}
	}
	
	// we go to stabilization mode in order to wait for the camera to settle down
	// after a period of bad data.
	private void toStabilizationMode() {
		switch(current_state) {
		case INIT:
		case IDLE:
			// This is the first state transisiton of the app. Go straight into stabilization
			// so the calibratoin will be clean.
			fixed_threshold = false;
			current_state = DAQActivity.state.STABILIZATION;
			L2Queue.clear();
			stabilization_counter = 0;
			xbManager.newExposureBlock();
			break;
			
		// coming out of calibration and data should be the same.
		case CALIBRATION:
		case DATA:
			Log.i(TAG, "FSM transition " + current_state.toString() + " -> STABILIZATION");
			
			// transition immediately
			current_state = DAQActivity.state.STABILIZATION;
			
			// clear the L2Queue.
			L2Queue.clear();
			
			// reset the stabilization counter
			stabilization_counter = 0;
			
			// mark the current XB as aborted and start a new one
			xbManager.abortExposureBlock();
			
			break;
		case STABILIZATION:
			// just reset the counter.
			stabilization_counter = 0;
			
			break;
		default:
			throw new RuntimeException("Bad FSM transition: " + current_state.toString() + " -> STABILZATION");
		}
	}
	
	public void toIdleMode() {
		// This mode is basically just used to cleanly close out any
		// current exposure blocks. For example, we transition here when
		// the phone is locked or the app is suspended.
		switch(current_state) {
		case IDLE:
			// nothing to do here...
			break;
		case CALIBRATION:
		case STABILIZATION:
			// for calibration or stabilization, mark the block as aborted
			current_state = DAQActivity.state.IDLE;
			xbManager.abortExposureBlock();
			break;
		case DATA:
			// for data, just close out the current XB
			current_state = DAQActivity.state.IDLE;
			xbManager.newExposureBlock();
			break;
		default:
			throw new RuntimeException("Bad FSM transition: " + current_state.toString() + " -> IDLE");
		}
	}
	
	public void generateRunConfig() {
		if (run_config != null) {
			// we've already constructed the object. nothing to do here.
		}
		DataProtos.RunConfig.Builder b = DataProtos.RunConfig.newBuilder();

		b.setIdHi(run_id.getMostSignificantBits());
		b.setIdLo(run_id.getLeastSignificantBits());
		b.setCrayfisBuild(build_version);
		b.setStartTime(System.currentTimeMillis());
		b.setCameraParams(mCamera.getParameters().flatten());
		
		run_config = b.build();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// FIXME: for debugging only!!! We need to figure out how
		// to keep DAQ going without taking over the phone.
		PowerManager pm = (PowerManager)
		getSystemService(Context.POWER_SERVICE); wl =
		pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
		
		// get the grav/accelerometer, if any
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		gravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		
		// Generate a UUID to represent this run
		run_id = UUID.randomUUID();
		
		device_id = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
		
		// load up the server-adjustable preferences from last time
		SharedPreferences localPrefs = getPreferences(Context.MODE_PRIVATE);
		calibration_sample_frames = localPrefs.getInt("calibration_sample_frames", default_calibration_sample_frames);
		target_events_per_minute = localPrefs.getFloat("target_events_per_minute", default_target_events_per_minute);
		xb_period = localPrefs.getInt("xb_period", default_xb_period);
		
		localPrefs.registerOnSharedPreferenceChangeListener(this);
		/*
		// clear saved settings (for debug purposes)
		SharedPreferences.Editor editor = localPrefs.edit();
		editor.clear();
		editor.commit();
		*/
		
		build_version = "unknown";
		build_version_code = 0;
		try {
			build_version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
			build_version_code = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		}
		catch (NameNotFoundException ex) {
			// don't know why we'd get here...
			Log.e(TAG, "Failed to resolve build version!");
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);

		// Used to visualize the results
		mDraw = new Visualization(this);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this, this, true);

		context = getApplicationContext();

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);
		preview.addView(mDraw);
		
		L1counter = L1proc = L1skip = 0;
		starttime = System.currentTimeMillis();

		LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location
				// provider.
				newLocation(location);
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};
		// get the manager
		locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// ask for updates from network and GPS
		// locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
		// 0, 0, locationListener);
		// locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
		// 0, 0, locationListener);

		// get the last known coordinates for an initial value
		currentLocation = locationManager
				.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (null == currentLocation) {
			currentLocation = new Location("BLANK");
		}
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		
		L1thresh = Integer
				.parseInt(sharedPrefs.getString("prefThreshold", "0"));
		
		xbManager = new ExposureBlockManager();
		
		// Set the initial state
		current_state = DAQActivity.state.INIT;
		
		// Spin up the output and image processing threads:
		outputThread = new OutputManager(this);
		outputThread.start();

		l2thread = new L2Processor();
		l2thread.start();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// make sure we're in IDLE state, to make sure
		// everything's closed.
		toIdleMode();
		
		// flush all the closed exposure blocks immediately.
		xbManager.flushCommittedBlocks(true);
		
		// stop the image processing thread.
		l2thread.stopThread();
		l2thread = null;
		
		// request to stop the OutputManager. It will automatically
		// try to upload any remaining data before dying.
		outputThread.stopThread();
		outputThread = null;
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		wl.acquire();
		
		Sensor sens = gravSensor;
		if (sens == null) {
			sens = accelSensor;
		}
		mSensorManager.registerListener(this, sens, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);

		if (mCamera != null)
			throw new RuntimeException(
					"Bug, camera should not be initialized already");
		
		// Transition to stabilization mode as we resume data-taking
		toStabilizationMode();

		// configure the camera parameters and start it acquiring frames.
		setUpAndConfigureCamera();

		if (reco == null) {
			// if we don't already have a particleReco object setup,
			// do that now that we know the camera size.
			reco = new ParticleReco(previewSize, this);
		}
		
		// once all the hardware is set up and the output manager is running,
		// we can generate and commit the runconfig
		if (run_config == null) {
			generateRunConfig();
			outputThread.commitRunConfig(run_config);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if (wl.isHeld())
			wl.release();
		
		mSensorManager.unregisterListener(this);
		
		Log.i(TAG, "Suspending!");

		// stop the camera preview and all processing
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			
			// clear out any (old) buffers
			L2Queue.clear();
			
			// If the app is being paused, we don't want that
			// counting towards the current exposure block.
			// So close it out cleaning by moving to the IDLE state.
			toIdleMode();
		}
	}

	/**
	 * Sets up the camera if it is not already setup.
	 */
	private void setUpAndConfigureCamera() {
		// Open and configure the camera
		mCamera = Camera.open();

		Camera.Parameters param = mCamera.getParameters();

		Log.d("params", "Camera params are" + param.flatten());

		// Select the preview size closest to 320x240
		// Smaller images are recommended because some computer vision
		// operations are very expensive
		List<Camera.Size> sizes = param.getSupportedPreviewSizes();
		// previewSize = sizes.get(closest(sizes,640,480));
		previewSize = sizes.get(closest(sizes, targetPreviewHeight, targetPreviewWidth));
		// Camera.Size previewSize = sizes.get(closest(sizes,176,144));

		Log.d("setup", "size is width=" + previewSize.width + " height =" + previewSize.height);
		param.setPreviewSize(previewSize.width, previewSize.height);
		// param.setFocusMode("FIXED");
		param.setExposureCompensation(0);
		
		// Try to pick the highest FPS range possible
		int minfps = 0, maxfps = 0;
		List<int[]> validRanges = param.getSupportedPreviewFpsRange();
		for (int[] rng : validRanges) {
			Log.i(TAG, "Supported FPS range: [ " + rng[0] + ", " + rng[1] + " ]");
			if (rng[1] > maxfps || (rng[1] == maxfps && rng[0] > minfps)) {
				maxfps = rng[1];
				minfps = rng[0];
			}
		}
		Log.i(TAG, "Selected FPS range: [ " + minfps + ", " + maxfps + " ]");
		try{
			// Try to set minimum=maximum FPS range.
			// This seems to work for some phones...
			param.setPreviewFpsRange(maxfps, maxfps);
			
			mCamera.setParameters(param);
		}
		catch (RuntimeException ex) {
			// but some phones will throw a fit. So just give it a "supported" range.
			Log.w(TAG, "Unable to set maximum frame rate. Falling back to default range.");
			param.setPreviewFpsRange(minfps, maxfps);
			
			mCamera.setParameters(param);
		}

		// Create an instance of Camera
		mPreview.setCamera(mCamera);
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

			Log.d("setup", "size is w=" + s.width + " x " + s.height);
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

	/**
	 * Called each time a new image arrives in the data stream.
	 */
	@Override
	public void onPreviewFrame(byte[] bytes, Camera camera) {
		// NB: since we are using NV21 format, we will be discarding some bytes at
		// the end of the input array (since we only need to grayscale output)
		
		
		// get a reference to the current xb, so it doesn't change from underneath us
		ExposureBlock xb = xbManager.getCurrentExposureBlock();
		
		L1counter++;
		xb.L1_processed++;
		
		long acq_time = System.currentTimeMillis();
		
		// for calculating fps
		if (L1counter % fps_update_interval == 0) {
			L1_fps_start = L1_fps_stop;
			L1_fps_stop = acq_time;
		}
		
		if (current_state == DAQActivity.state.CALIBRATION) {
			// In calbration mode, there's no need for L1 trigger; just go straight to L2
			boolean queue_accept = L2Queue.offer(new RawCameraFrame(bytes, acq_time, xb, orientation));
			
			if (! queue_accept) {
				// oops! the queue is full... this frame will be dropped.
				Log.e(TAG, "Could not add frame to L2 Queue!");
				L2busy++;
			}
			
			return;
		}
		
		if (current_state == DAQActivity.state.STABILIZATION) {
			// If we're in stabilization mode, just drop frames until we've skipped enough
			stabilization_counter++;
			if (stabilization_counter > stabilization_sample_frames) {
				// We're done! Go to calibration.
				toCalibrationMode();
			}
			
			return;
		}
		
		if (current_state == DAQActivity.state.IDLE) {
			// Not sure why we're still acquiring frames in IDLE mode...
			Log.w(TAG, "Frames still being recieved in IDLE mode");
			return;
		}
		
		// prescale
		if (L1counter % L1prescale == 0) {
			// make sure there's room on the queue
			if (L2Queue.remainingCapacity() > 0) {
				// check if we pass the L1 threshold
				boolean pass = false;
				int length = previewSize.width * previewSize.height;
				for (int i = 0; i < length; i++) {
					// make sure we promote the (signed) byte to int for comparison!
					if ( (bytes[i] & 0xFF) > L1thresh) {
						// Okay, found a pixel above threshold. No need to continue
						// looping.
						pass = true;
						break;
					}
				}
				if (pass) {
					xb.L1_pass++;

					// this frame has passed the L1 threshold, put it on the
					// L2 processing queue.
					boolean queue_accept = L2Queue.offer(new RawCameraFrame(bytes, acq_time, xb, orientation));
					
					if (! queue_accept) {
						// oops! the queue is full... this frame will be dropped.
						Log.e(TAG, "Could not add frame to L2 Queue!");
						L2busy++;
					}
				}
				L1proc++;
			}
			else {
				// no room on the L2 queue! We'll have to skip this frame.
				L1skip++;
				xb.L1_skip++;
			}

		}

		// Can only do trivial amounts of image processing inside this function
		// or else bad stuff happens.
		// To work around this issue most of the processing has been pushed onto
		// a thread and the call below
		// tells the thread to wake up and process another image

	}

	// ///////////////////////////////////////

	/**
	 * Draws on top of the video stream for visualizing computer vision results
	 */
	private class Visualization extends SurfaceView {

		Paint mypaint;
		Paint mypaint2;
		//Paint mypaint2_thresh;
		Paint mypaint3;
		Paint mypaint_warning;
		Paint mypaint_info;

		private String[] histo_strings_all = new String[256];
		private String[] histo_strings_thresh = new String[256];

		void makeHistogram(int data[], int min, String[] histo_strings) {
			int max = 1;
			for (int i = 0; i < 256; i++)
				if (data[i] > max)
					max = data[i];
			// make 256 vertical devisions
			// each one is at log(data)/log(max) * i /256

			// height loop

			for (int j = 256; j > 0; j--) {
				// width loop
				for (int i = 0; i < 256; i++) {
					if (i < min
							|| data[i] == 0
							|| java.lang.Math.log(data[i]) < java.lang.Math
									.log(max) * (j / 256.0))
						// if ( data[i] < max*j/256.0 )
						histo_chars[j - 1][i] = ' ';
					else
						histo_chars[j - 1][i] = '*';
				}
				histo_strings[j - 1] = new String(histo_chars[j - 1]);
			}
		}

		public Visualization(Activity context) {
			super(context);

			mypaint = new Paint();
			mypaint2 = new Paint();
			//mypaint2_thresh = new Paint();

			mypaint3 = new Paint();
			
			mypaint_warning = new Paint();
			mypaint_info = new Paint();

			// This call is necessary, or else the
			// draw method will not be called.
			setWillNotDraw(false);
		}

		@Override
		protected void onDraw(Canvas canvas) {

			synchronized (lockOutput) {
				int w = canvas.getWidth();
				int h = canvas.getHeight();

				// fill the window and center it
				double scaleX = w / (double) 640.0;
				double scaleY = h / (double) 480.0;

				double scale = Math.min(scaleX, scaleY);
				double tranX = (w - scale * 640.0) / 2;
				double tranY = (h - scale * 480.0) / 2;

				canvas.translate((float) tranX, (float) tranY);
				canvas.scale((float) scale, (float) scale);

				// draw some data text for debugging
				int tsize = 25;
				int yoffset = -300;
				mypaint.setStyle(android.graphics.Paint.Style.FILL);
				mypaint.setColor(android.graphics.Color.RED);
				mypaint.setTextSize((int) (tsize * 1.5));
				
				mypaint_warning.setStyle(android.graphics.Paint.Style.FILL);
				mypaint_warning.setColor(android.graphics.Color.YELLOW);
				mypaint_warning.setTextSize((int) (tsize * 1.1));
								
				mypaint_info.setStyle(android.graphics.Paint.Style.FILL);
				mypaint_info.setColor(android.graphics.Color.MAGENTA);
				mypaint_info.setTextSize((int) (tsize * 1.1));

				mypaint3.setStyle(android.graphics.Paint.Style.FILL);
				mypaint3.setColor(android.graphics.Color.GRAY);
				mypaint3.setTextSize(tsize);

				mypaint2.setStyle(android.graphics.Paint.Style.FILL);
				mypaint2.setColor(android.graphics.Color.WHITE);
				mypaint2.setTextSize(tsize / (float) 10.0);
				Typeface tf = Typeface.create("Courier", Typeface.NORMAL);
				mypaint2.setTypeface(tf);
				
				long exposed_time = xbManager.getExposureTime();
				canvas.drawText(
						"Exposure: "
								+ (int) (1.0e-3 * exposed_time)
								+ "s", 200, yoffset + 4 * tsize, mypaint);
				canvas.drawText("Events : " + totalEvents, 200, yoffset + 6 * tsize,
						mypaint);
				canvas.drawText("Pixels : " + totalPixels, 200, yoffset + 8 * tsize,
						mypaint);

				canvas.drawText("XBs: " + committedXBs, 200, yoffset + 10
						* tsize, mypaint);

				// /// Histogram
				makeHistogram(reco.h_pixel.values, 0, histo_strings_all);
				for (int j = 256; j > 0; j--)
					canvas.drawText(histo_strings_all[j - 1], 50,
							(float) (yoffset + (256 - j) * tsize / 10.0),
							mypaint2);
				
				for (int i = 0; i < 256; i++)
					if (i % 10 == 0)
						labels[i] = '|';
					else
						labels[i] = ' ';

				String slabels = new String(labels);
				String slabelsnum = new String(
						"0              100               200");
				canvas.drawText(slabels, (float) (50),
						(float) (yoffset + (256 + 5) * tsize / 10.0), mypaint2);
				canvas.drawText(slabelsnum, (float) (42),
						(float) (yoffset + (256 + 14) * tsize / 10.0), mypaint3);

				canvas.drawText("Pixel value",
						(float) (50 + 20 * tsize / 10.0),
						(float) (yoffset + (256 + 25) * tsize / 10.0), mypaint3);

				String state_message = "";
				switch (current_state) {
				case CALIBRATION:
					state_message = current_state.toString() + " "
							+ (int)(( 100.0 * (float) reco.event_count) / calibration_sample_frames) + "%";
					break;
				case DATA:
					state_message = current_state.toString() + " (L1=" + L1thresh
						+ ",L2=" + L2thresh + ")";
					break;
				case STABILIZATION:
					state_message = current_state.toString() + " "
							+ (int)(( 100.0 * (float) stabilization_counter) / stabilization_sample_frames) + "%";
					break;
				default:
					state_message = current_state.toString();
				}
				canvas.drawText(state_message, 200, yoffset + 12 * tsize, mypaint);
				
				String fps = "---";
				if (L1_fps_start > 0 && L1_fps_stop > 0) {
					fps = String.format("%.1f", (float) fps_update_interval / (L1_fps_stop - L1_fps_start) * 1e3);
				}
				canvas.drawText(fps + " fps",
						200, yoffset + 14 * tsize, mypaint);
				
				canvas.drawLine(195, yoffset + 14 * tsize, 195, yoffset + 3
						* tsize, mypaint);
				
				if (! outputThread.canUpload()) {
					if (outputThread.permit_upload) {
						canvas.drawText("Warning! Network unavailable.", 250, yoffset+15 * tsize, mypaint_warning);
					} else {
						canvas.drawText("Server is overloaded!", 240, yoffset+15 * tsize, mypaint_warning);
						canvas.drawText("Saving data locally.", 240, yoffset+16 * tsize, mypaint_warning);
					}
				}
				
				if (L2busy > 0) {
					// print a message indicating that we've been dropping frames
					// due to L2queue overflow.
					canvas.drawText("Warning! L2busy (" + L2busy + ")", 250, yoffset+ 16 * tsize, mypaint_warning);
				}
				
				String device_msg = "dev: ";
				if (outputThread.device_nickname != null) {
					device_msg += outputThread.device_nickname + " (" + device_id + ")";
				}
				else {
					device_msg += device_id;
				}
				String run_msg = "run: " + run_id.toString().substring(19);

				canvas.drawText(build_version, 175, yoffset + 18 * tsize, mypaint_info);
				canvas.drawText(device_msg, 175, yoffset + 19*tsize, mypaint_info);
				canvas.drawText(run_msg, 175, yoffset+20*tsize, mypaint_info);
				if (outputThread.current_experiment != null) {
					String exp_msg = "exp: " + outputThread.current_experiment;
					canvas.drawText(exp_msg, 175, yoffset+21*tsize, mypaint_info);
				}
				
				canvas.save();
				canvas.rotate(-90, (float) (50 + -7 * tsize / 10.0),
						(float) (yoffset + (256 - 50) * tsize / 10.0));
				canvas.drawText(String.format("Number of pixels"),
						(float) (50 + -7 * tsize / 10.0),
						(float) (yoffset + (256 - 50) * tsize / 10.0), mypaint3);
				canvas.restore();

			}

		}
	}
	
	private class ExposureBlockManager {
		public static final String TAG = "ExposureBlockManager";
		
		// This is where the current xb is kept. The DAQActivity must access
		// the current exposure block through the public methods here.
		private ExposureBlock current_xb;
		
		// We keep a list of retired blocks, which have been closed but
		// may not be ready to commit yet (e.g. events belonging to this block
		// might be sequested in a queue somewhere, still)
		private LinkedList<ExposureBlock> retired_blocks = new LinkedList<ExposureBlock>();
		
		private long safe_time = 0;
		
		private long committed_exposure;
		
		// Atomically check whether the current XB is to old, and if so,
		// create a new one. Then return the current block in either case.
		public synchronized ExposureBlock getCurrentExposureBlock() {
			if (current_xb == null) {
				// not sure what to do here?
				return null;
			}
			
			// check and see whether this XB is too old
			if (current_xb.age() > xb_period * 1000) {
				newExposureBlock();
			}
			
			return current_xb;
		}
		
		// Return an estimate of the exposure time in committed + current
		// exposure blocks.
		public synchronized long getExposureTime() {
			if (current_xb.daq_state == DAQActivity.state.DATA) {
				return committed_exposure + current_xb.age();
			}
			else {
				return committed_exposure;
			}
		}

		public synchronized void newExposureBlock() {
			if (current_xb != null) {
				current_xb.freeze();
				retireExposureBlock(current_xb);
			}

			Log.i(TAG, "Starting new exposure block!");
			current_xb = new ExposureBlock();
			totalXBs++;
			
			current_xb.xbn = totalXBs;
			current_xb.L1_thresh = L1thresh;
			current_xb.L2_thresh = L2thresh;
			current_xb.start_loc = new Location(currentLocation);
			current_xb.daq_state = current_state;
			current_xb.run_id = run_id;
		}
		
		public synchronized void abortExposureBlock() {
			current_xb.aborted = true;
			newExposureBlock();
		}
		
		private void retireExposureBlock(ExposureBlock xb) {
			// anything that's being committed must have already been frozen.
			assert xb.frozen == true;
			retired_blocks.add(xb);
			
			// if this is a DATA block, add its age to the commited
			// exposure time.
			if (xb.daq_state == DAQActivity.state.DATA) {
				committed_exposure += xb.age();
			}
		}

		public void updateSafeTime(long time) {
			// this time should be monotonically increasing
			assert time >= safe_time;
			safe_time = time;
		}
		
		public void flushCommittedBlocks() {
			// Try to flush out any committed exposure blocks that
			// have no new events coming.
			if (retired_blocks.size() == 0) {
				// nothing to be done.
				return;
			}
			
			for (Iterator<ExposureBlock> it = retired_blocks.iterator(); it.hasNext(); ) {
				ExposureBlock xb = it.next();
				if (xb.end_time < safe_time) {
					// okay, it's safe to commit this block now.
					it.remove();
					commitExposureBlock(xb);
				}
			}
		}
		
		public void flushCommittedBlocks(boolean force) {
			// If force == true, immediately flush all blocks.
			if (force) {
				updateSafeTime(System.currentTimeMillis());
			}
			flushCommittedBlocks();
		}
		
		private void commitExposureBlock(ExposureBlock xb) {
			if (xb.daq_state == DAQActivity.state.STABILIZATION
					|| xb.daq_state == DAQActivity.state.IDLE) {
				// don't commit stabilization/idle blocks! they're just deadtime.
				return;
			}
			if (xb.daq_state == DAQActivity.state.CALIBRATION
				&& xb.aborted) {
				// also, don't commit *aborted* calibration blocks
				return;
			}
			
			Log.i(TAG, "Commiting old exposure block!");
			
			boolean success = outputThread.commitExposureBlock(xb);
			
			if (! success) {
				// Oops! The output manager's queue must be full!
				throw new RuntimeException("Oh no! Couldn't commit an exposure block. What to do?");
			}
			
			committedXBs++;
		}
	}

	/**
	 * External thread used to do more time consuming image processing
	 */
	private class L2Processor extends Thread {

		// true if a request has been made to stop the thread
		volatile boolean stopRequested = false;
		// true if the thread is running and can process more data
		volatile boolean running = true;

		/**
		 * Blocks until the thread has stopped
		 */
		public void stopThread() {
			stopRequested = true;
			while (running) {
				l2thread.interrupt();
				Thread.yield();
			}
		}

		@Override
		public void run() {

			while (!stopRequested) {
				boolean interrupted = false;

				RawCameraFrame frame = null;
				try {
					// Grab a frame buffer from the queue, blocking if none
					// is available.
					frame = L2Queue.poll(L2Timeout, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					// Interrupted, possibly by app shutdown?
					Log.d(TAG, "L2 processing interrupted while waiting on queue.");
					interrupted = true;
				}
				
				// update the GUI (does it make sense to do handle this somewhere else?)
				mDraw.postInvalidate();
				
				// also try to clear out any old committed XB's that are sitting around.
				xbManager.flushCommittedBlocks();
				
				if (interrupted) {
					// Somebody is trying to wake us. Bail out of this loop iteration
					// so we can check stopRequested.
					// Note that frame is null, so we're not loosing any data.
					continue;
				}
				
				if (frame == null) {
					// We must have timed out on an empty queue (or been interrupted).
					// If no new exposure blocks are coming, we can update the last known
					// safe time to commit XB's (with a small fudge factor just incase there's
					// something making its way onto the L2 queue now)
					xbManager.updateSafeTime(System.currentTimeMillis() - 1000);
					continue;
				}
				
				// If we made it this far, we have a real data frame ready to go.
				// First update the XB manager's safe time (so it knows which old XB
				// it can commit. 
				xbManager.updateSafeTime(frame.acq_time);
				
				ExposureBlock xb = frame.xb;
				
				xb.L2_processed++;
				
				if (L2counter % L2prescale != 0) {
					// prescaled! Drop the event.
					xb.L2_skip++;
					L2skip++;
					
					continue;
				}
				
				L2proc++;
				// FIXME: should we worry about setting currentLocation at
				// L1 (acquisition) time? Or is it okay here?
				frame.location = new Location(currentLocation);
				
				// First, build the event from the raw frame.
				RecoEvent event = reco.buildEvent(frame);
				
				// If we got a bad frame, go straight to stabilization mode.
				if (reco.good_quality == false
						&& fixed_threshold == false) {
					toStabilizationMode();
					continue;
				}
				
				xb.L2_pass++;
				
				// Now do the L2 (pixel-level analysis)
				ArrayList<RecoPixel> pixels;
				if (current_state == DAQActivity.state.DATA) {
					pixels = reco.buildL2Pixels(frame, xb.L2_thresh);
				}
				else {
					// Don't bother with full pixel reco and L2 threshold
					// if we're not actually taking data.
					pixels = reco.buildL2PixelsQuick(frame, 0);
				}
				
				// Now add them to the event.
				event.pixels = pixels;
				
				if (current_state == DAQActivity.state.DATA) {
					// keep track of the running totals for acquired
					// events/pixels over the app lifetime.
					totalEvents++;
					totalPixels += pixels.size();
				}
				
				L2counter++;
				
				// Finally, add the event to the proper exposure block.
				xb.addEvent(event);
				
				// If we're calibrating, check if we've processed enough
				// frames to decide on the threshold(s) and go back to
				// data-taking mode.
				if (current_state == DAQActivity.state.CALIBRATION
						&& reco.event_count >= calibration_sample_frames) {
					// mark the time of the last event from the run.
					calibration_stop = frame.acq_time;
					toDataMode();
					continue;
				}
			}
			running = false;
		}
	}


	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == gravSensor.getType()) {
			// get the gravity vector:
			gravity[0] = event.values[0];
			gravity[1] = event.values[1];
			gravity[2] = event.values[2];
		}
		if (event.sensor.getType() == magSensor.getType()) {
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
	
}
