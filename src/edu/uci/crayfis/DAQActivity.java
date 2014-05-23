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

import java.io.DataOutputStream;
//import android.preference.PreferenceFragment;
//import android.preference.PreferenceManager;
//import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.uci.crayfis.ParticleReco.RecoEvent;
import edu.uci.crayfis.ParticleReco.RecoPixel;
import edu.uci.crayfis.R;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;

/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends Activity implements Camera.PreviewCallback {

	public static final String TAG = "DAQActivity";

	// camera and display objects
	private Camera mCamera;
	private Visualization mDraw;
	private CameraPreview mPreview;

	// WakeLock to prevent the phone from sleeping during DAQ
	PowerManager.WakeLock wl;

	private char[][] histo_chars = new char[256][256];
	private int histo_max = 0;
	// draw some X axis labels
	char[] labels = new char[256];
	// settings
	private boolean uploadData = true;

	public static final int targetPreviewWidth = 320;
	public static final int targetPreviewHeight = 240;
	
	private static final int maxFrames = 2;

	// simple class to group a timestamp and byte array together
	public class RawCameraFrame {
		public final byte[] bytes;
		public final long acq_time;
		public Location location;
		
		public RawCameraFrame(byte[] bytes, long t) {
			this.bytes = bytes;
			this.acq_time = t;
		}
	}
	
	// Queue for frames to be processed by the L2 thread
	ArrayBlockingQueue<RawCameraFrame> L2Queue = new ArrayBlockingQueue<RawCameraFrame>(maxFrames);
	
	public String device_id;
	public String build_version;
	public UUID run_id;
	DataProtos.RunConfig run_config;
	
	// max amount of time to wait on L2Queue (seconds)
	int L2Timeout = 1;

	// to keep track of height/width
	private Camera.Size previewSize;

	// Android image data used for displaying the results
	// private Bitmap output;

	// how many events L1 skipped (after prescale)
	private int L1skip = 0;
	// how many events L1 processed (after prescale)
	private int L1proc = 0;
	private int L1pass = 0;

	// how many events L1 skipped (after prescale)
	private int L2skip = 0;
	// how many events L1 processed (after prescale)
	private int L2proc = 0;

	// how many particles seen > thresh
	private int totalPixels = 0;
	private int numFiles = 0;
	private int totalEvents = 0;
	private int totalXBs = 0;

	private long L1counter = 0;
	private long L2counter = 0;

	private int L1prescale = 1;
	private int L2prescale = 1;

	private int uploadDelta = 10; // number of seconds to wait before checking for new files

	public enum state {
		INIT, CALIBRATION, DATA
	};

	private state current_state;
	private boolean fixed_threshold;
	
	private long calibration_start;
	
	// how many frames to sample during calibration
	// (more frames is longer but gives better statistics)
	private int calibration_sample_frames = 1000;
	// targeted max. number of events per minute to allow
	// (lower rate => higher threshold)
	private float max_events_per_minute = 50;

	private ExposureBlock current_xb;
	// Previous xb is kept around until either the L2 queue
	// is empty, or an event outside the block is processed.
	private ExposureBlock previous_xb;
	
	// the nominal period for an exposure block (in seconds)
	public static final long xb_period = 30;

	// L1 hit threshold
	private int L1thresh = 0;
	private int L2thresh = 5;
	long starttime;
	long lastUploadTime;
	long lastL2time;
	private int writeIndex;
	private int readIndex;

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
	private ThreadProcess l2thread;

	// thread to upload the data
	private UploadProcess uploadthread;
	
	// thread to handle output of committed data
	private OutputManager outputThread;

	// class to find particles in frames
	private ParticleReco reco;

	// Object used for synchronizing output image
	private final Object lockOutput = new Object();

	Context context;

	public void clickedRegister(View view) {

		Intent i = new Intent(this, UserRegisterActivity.class);
		startActivity(i);
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
		/*
		 * AlertDialog.Builder alertDialogBuilder = new
		 * AlertDialog.Builder(this);
		 * 
		 * 
		 * 
		 * // set title alertDialogBuilder.setTitle("About CRAYFIS");
		 * 
		 * // set dialog message alertDialogBuilder .setMessage(Html.fromHtml(
		 * "CRAYFIS: <a href=\"http://crayfis.ps.uci.edu\">details</a>"))
		 * .setCancelable(false) .setPositiveButton("Ok",new
		 * DialogInterface.OnClickListener() { public void
		 * onClick(DialogInterface dialog,int id) {
		 * 
		 * } });
		 * 
		 * // create alert dialog AlertDialog alertDialog =
		 * alertDialogBuilder.create();
		 * 
		 * // show it alertDialog.show();
		 */
	}

	private void newLocation(Location location) {
		currentLocation = location;
	}
	
	private void toCalibrationMode() {
		switch (current_state) {
		case INIT:
			Log.i(TAG, "FSM Transition: INIT -> CALIBRATION");

			fixed_threshold = false;
			
			// during INIT state, the reco object may not exist yet...
			//reco.clearHistograms();
			
			calibration_start = System.currentTimeMillis();
			current_state = DAQActivity.state.CALIBRATION;
			
			//L1thresh = 0;
			//L2thresh = 0;
			
			// Start a new exposure block
			newExposureBlock();
			
			break;
		case DATA:
			Log.i(TAG, "FSM Transition: DATA -> CALIBRATION");
			
			// clear histograms and counters before calibration
			reco.reset();
			
			calibration_start = System.currentTimeMillis();
			current_state = DAQActivity.state.CALIBRATION;
			
			// Start a new exposure block
			newExposureBlock();
			
			break;
		case CALIBRATION:
			Log.i(TAG, "FSM Transition: CALIBRATION -> CALIBRATION");
			
			// as far as I can tell, this just happens when we have "bad"
			// frames during calibration. the only difference here is
			// we'll stay on the same exposure block.
			// clear histogram before calibration
			reco.reset();
			
			calibration_start = System.currentTimeMillis();
			current_state = DAQActivity.state.CALIBRATION;
			
			break;	
		default:
			throw new RuntimeException("Bad FSM transition to CALIBRATION");
		}
	}
	
	private void toDataMode() {
		switch (current_state) {
		case INIT:
			Log.i(TAG, "FSM Transition: INIT -> DATA");
			fixed_threshold = true;
			current_state = DAQActivity.state.DATA;
			
			// Start a new exposure block
			newExposureBlock();
			
			break;
		case CALIBRATION:
			Log.i(TAG, "FSM Transition: CALIBRATION -> DATA");

			int new_thresh;
			long calibration_time = System.currentTimeMillis() - calibration_start;
			int target_events = (int) (max_events_per_minute * calibration_time* 1e-3 / 60.0);
			
			Log.i("Calibration", "Processed " + reco.max_histo_count + " frames in " + (int)(calibration_time*1e-3) + " s; target events = " + target_events);
			
			new_thresh = reco.calculateThresholdByEvents(target_events);
			
			Log.i(TAG, "Setting new L1 threshold: {"+ L1thresh + "} -> {" + new_thresh + "}");
			L1thresh = new_thresh;
			if (L1thresh < L2thresh) {
				// If we chose an L1threshold lower than the L2 threshold, then
				// we should lower it!
				L2thresh = L1thresh;
			}
			
			// clear list of particles
			reco.particles_size = 0;
			current_state = DAQActivity.state.DATA;

			// Start a new exposure block
			newExposureBlock();
			
			break;
		default:
			throw new RuntimeException("Bad FSM transition to DATA");
		}
	}
	
	// Start a new exposure block, freezing the current one (if any)
	void newExposureBlock() {
		Log.i(TAG, "Sarting new exposure block!");
		
		// by the time we start a new XB, we had better have committed the
		// previous one already!
		if (previous_xb != null) {
			throw new RuntimeException("Oh crud! There's still a previous XB sitting around");
		}
		
		// Freeze the current XB, and push it back to the temp slot
		previous_xb = current_xb;
		current_xb = new ExposureBlock();
		totalXBs++;
		
		current_xb.L1_thresh = L1thresh;
		current_xb.L2_thresh = L2thresh;
		current_xb.start_loc = new Location(currentLocation);
		current_xb.daq_state = current_state;
		current_xb.run_id = run_id;
		
		if (previous_xb != null) {
			previous_xb.freeze();
			Log.i(TAG, "Old exposure block started at: " + previous_xb.start_time + ", ended at: " + previous_xb.end_time);
		}
	}
	
	// Commit the previous exposure block (if any).
	// This is to be called when once we determine the previous
	// block has no more data coming. I.e. when:
	//  1) we have processed a frame that is too new for this XB
	//  2) we have timed out on an empty L2 queue (no recent data from L1) 
	void commitExposureBlock() {
		if (previous_xb == null) {
			return;
		}
		Log.i(TAG, "Commiting old exposure block!");
		
		ExposureBlock xb = previous_xb;
		previous_xb = null;
		boolean success = outputThread.commitExposureBlock(xb);
		
		if (! success) {
			// Oops! The output manager's queue must be full!
			throw new RuntimeException("Oh no! Couldn't commit an exposure block. What to do?");
		}
	}
	
	public void generateRunConfig() {
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

		/*
		 * for debugging PowerManager pm = (PowerManager)
		 * getSystemService(Context.POWER_SERVICE); wl =
		 * pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
		 * wl.acquire();
		 */
		
		// Generate a UUID to represent this run
		run_id = UUID.randomUUID();
		
		device_id = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
		build_version = "unknown";
		try {
			build_version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException ex) {
			// don't know why we'd get here...
			Log.e(TAG, "Failed to resolve build version!");
		}

		// getFragmentManager().beginTransaction().replace(android.R.id.content,
		// new PrefsFragment()).commit();

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);

		// Used to visualize the results
		mDraw = new Visualization(this);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this, this, true);

		context = getApplicationContext();
		
		// Resolve the build version for upload purposes.
		try {
			PackageInfo pInfo = getPackageManager().getPackageInfo(
					getPackageName(), 0);
			//dstorage.versionName = pInfo.versionName;
			//dstorage.versionCode = pInfo.versionCode;

			Log.d(TAG, "resolved versionName = " + pInfo.versionName
					+ ", versionCode = " + pInfo.versionCode);
		} catch (PackageManager.NameNotFoundException ex) {
			Log.e(TAG, "Couldn't resolve package version", ex);
		}

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);
		preview.addView(mDraw);
		writeIndex = 0;
		readIndex = 0;
		L1counter = L1proc = L1skip = L1pass = 0;
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
		// gget the manager
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
		
		current_xb = null;
		previous_xb = null;
		current_state = DAQActivity.state.INIT;
		
		if (L1thresh >= 0) {
			toDataMode();

		} else {
			toCalibrationMode();
		}
	}

	@Override
	protected void onDestroy() { /* wl.release(); */
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mCamera != null)
			throw new RuntimeException(
					"Bug, camera should not be initialized already");

		setUpAndConfigureCamera();

	}

	@Override
	protected void onPause() {
		super.onPause();
		
		Log.i(TAG, "Suspending!");

		// stop the camera preview and all processing
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			
			// clear out the (old) buffers
			L2Queue.clear();

			l2thread.stopThread();
			l2thread = null;
			uploadthread.stopThread();
			uploadthread = null;
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
		mCamera.setParameters(param);
		
		// now that all the hardware is set up, generate the runconfig metadata.
		generateRunConfig();
		
		reco = new ParticleReco(previewSize);
		
		outputThread = new OutputManager(context);
		outputThread.start();
		
		// once the output manager is running, we can commit the runconfig
		outputThread.commitRunConfig(run_config);
		
		// start image processing thread
		l2thread = new ThreadProcess();
		l2thread.start();

		// start data upload thread
		uploadthread = new UploadProcess();
		uploadthread.start();

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
		ExposureBlock xb = current_xb;
		
		L1counter++;
		xb.L1_processed++;
		
		long acq_time = System.currentTimeMillis();
		
		// for calculating fps
		if (L1counter % fps_update_interval == 0) {
			L1_fps_start = L1_fps_stop;
			L1_fps_stop = acq_time;
		}
		
		if (current_state == DAQActivity.state.CALIBRATION) {
			// No need for L1 trigger; just go straight to L2
			boolean queue_accept = L2Queue.offer(new RawCameraFrame(bytes, acq_time));
			
			if (! queue_accept) {
				// oops! the queue is full. how did that happen, we're the only ones
				// using it! (shouldn't get here...)
				Log.e(TAG, "Could not add frame to L2 Queue!");
			}
			
			return;
		}
		
		// prescale
		// Log.d("preview","Camera vals are"+camera.getParameters.flatten());
		// Log.d("preview"," ="+L1counter+" mod = "+L1counter%L1prescale);
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
					L1pass++;
					xb.L1_pass++;

					// this frame has passed the L1 threshold, put it on the
					// L2 processing queue.
					boolean queue_accept = L2Queue.offer(new RawCameraFrame(bytes, acq_time));
					
					if (! queue_accept) {
						// oops! the queue is full. how did that happen, we're the only ones
						// using it! (shouldn't get here...)
						Log.e(TAG, "Could not add frame to L2 Queue!");
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

		Activity activity;
		Paint mypaint;
		Paint mypaint2;
		Paint mypaint2_thresh;
		Paint mypaint3;

		private String[] histo_strings_all = new String[256];
		private String[] histo_strings_thresh = new String[256];

		void makeHistogram(int data[], int min, String[] histo_strings) {
			int max = 1;
			for (int i = 0; i < 256; i++)
				if (data[i] > max)
					max = data[i];
			histo_max = max;
			// make 256 vertical devisions
			// each one is at log(data)/log(max) * i /256

			int ip = 0;
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
			this.activity = context;

			mypaint = new Paint();
			mypaint2 = new Paint();
			mypaint2_thresh = new Paint();

			mypaint3 = new Paint();

			// This call is necessary, or else the
			// draw method will not be called.
			setWillNotDraw(false);
		}

		@Override
		protected void onDraw(Canvas canvas) {

			SharedPreferences sharedPrefs = PreferenceManager
					.getDefaultSharedPreferences(context);

			// Log.d("frames","onDraw() called");
			boolean doReco = sharedPrefs.getBoolean("prefDoReco", true);

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

				mypaint3.setStyle(android.graphics.Paint.Style.FILL);
				mypaint3.setColor(android.graphics.Color.GRAY);
				mypaint3.setTextSize(tsize);

				mypaint2.setStyle(android.graphics.Paint.Style.FILL);
				mypaint2.setColor(android.graphics.Color.WHITE);
				mypaint2.setTextSize(tsize / (float) 10.0);
				Typeface tf = Typeface.create("Courier", Typeface.NORMAL);
				mypaint2.setTypeface(tf);

				mypaint2_thresh.setStyle(android.graphics.Paint.Style.FILL);
				mypaint2_thresh.setColor(android.graphics.Color.GREEN);
				mypaint2_thresh.setTextSize(tsize / (float) 10.0);

				float deadtime = L1skip / ((float) L1skip + L1proc);
				float l2deadtime = L2skip / ((float) L2skip + L2proc);

				float l1rate = (L1counter)
						/ ((float) 1e-3 * (float) (System.currentTimeMillis() - starttime));
				float l2rate = (L2counter)
						/ ((float) 1e-3 * (float) (System.currentTimeMillis() - starttime));
				// canvas.drawText("STATISTICS",250, yoffset+1*tsize, mypaint);
				canvas.drawText(
						"Time: "
								+ (int) (1.0e-3 * (float) (System
										.currentTimeMillis() - starttime))
								+ "s", 200, yoffset + 4 * tsize, mypaint);
				// canvas.drawText("Frames "+L1proc+" pass?"+L1pass+" analyzed="+L2proc+" skipped="+L2skip,250,yoffset+3*tsize,mypaint);
				canvas.drawText("Events : " + totalEvents, 200, yoffset + 6 * tsize,
						mypaint);
				canvas.drawText("Pixels : " + totalPixels, 200, yoffset + 8 * tsize,
						mypaint);
				
				// canvas.drawText("Loc: "+String.format("%1.2f",currentLocation.getLongitude())+", "+String.format("%1.2f",currentLocation.getLatitude()),
				// 250,15+5*tsize,mypaint);
				// canvas.drawText("Data quality good? "+reco.good_quality,250,15+6*tsize,mypaint);

				canvas.drawText("XBs: " + totalXBs, 200, yoffset + 10
						* tsize, mypaint);

				// /// Histogram
				makeHistogram(reco.h_pixel.values, 0, histo_strings_all);
				for (int j = 256; j > 0; j--)
					canvas.drawText(histo_strings_all[j - 1], 50,
							(float) (yoffset + (256 - j) * tsize / 10.0),
							mypaint2);

				makeHistogram(reco.h_pixel.values, L1thresh, histo_strings_thresh);
				for (int j = 256; j > 0; j--)
					canvas.drawText(histo_strings_thresh[j - 1], 50,
							(float) (yoffset + (256 - j) * tsize / 10.0),
							mypaint2_thresh);

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

				if (current_state == DAQActivity.state.CALIBRATION)
					canvas.drawText(
							current_state.toString() + " "
									+ (int)(( 100.0 * (float) reco.event_count) / calibration_sample_frames)
									+ "%", 200, yoffset + 12 * tsize, mypaint);
				else
					canvas.drawText(current_state.toString() + " (L1=" + L1thresh
							+ ",L2=" + L2thresh + ")", 200, yoffset + 12 * tsize, mypaint);
				
				String fps = "---";
				if (L1_fps_start > 0 && L1_fps_stop > 0) {
					fps = String.format("%.1f", (float) fps_update_interval / (L1_fps_stop - L1_fps_start) * 1e3);
				}
				canvas.drawText(fps + " fps",
						200, yoffset + 14 * tsize, mypaint);
				
				canvas.drawLine(195, yoffset + 14 * tsize, 195, yoffset + 3
						* tsize, mypaint);

				// canvas.drawText("Threshold: "+L1thresh,250,15+12*tsize,mypaint);

				// Y axis labels

				// draw grid for debugging
				/*
				 * for (int x = -500;x<1500;x+=250) for (int y =
				 * -500;y<1500;y+=250)
				 * canvas.drawText("(x="+x+",y="+y+")",(float
				 * )x,(float)y,mypaint3);
				 */
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

	private class UploadProcess extends Thread {
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
				uploadthread.interrupt();
				Thread.yield();
			}
		}

		@Override
		public void run() {
			SharedPreferences sharedPrefs = PreferenceManager
					.getDefaultSharedPreferences(context);
			boolean doUpload = sharedPrefs.getBoolean("prefUploadData", true);

			while (!stopRequested) {
				// check datastorage for size, upload if too bit
				// needs to be buffered if no connection
				// Log.d("uploads"," reco? "+doReco+" uploads? "+uploadData);

				// occasionally upload files
				if (uploadData) {					
					// TODO: upload committed exposure blocks
					
					try {
						Thread.sleep(uploadDelta * 1000);
					}
					catch (InterruptedException ex) {
						// Somebody interrupted this thread, probably a shutdown request.
					}
				}
			}

			running = false;
		}
	}

	/**
	 * External thread used to do more time consuming image processing
	 */
	private class ThreadProcess extends Thread {

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
				
				// *yawn*, we've just come off the blocking queue. Not matter the reason,
				// check if it is time to create a new exposure block:
				if (current_xb.age() > (xb_period * 1000)) {
					newExposureBlock();
				}
				
				// also update the GUI (does it make sense to do handle this somewhere else?)
				mDraw.postInvalidate();
				
				if (interrupted) {
					// Somebody is trying to wake us. Bail out of this loop iteration
					// so we can check stopRequested.
					// Note that frame is null, so we're not loosing any data.
					continue;
				}
				
				if (frame == null) {
					// We must have timed out on an empty queue (or been interrupted).
					// If there is a previous exposure block waiting, we are done with it now.
					// Other than that, there's nothing to do.
					commitExposureBlock();
					continue;
				}
				
				// Okay, if we made it here, we've got an actual data frame
				// that is ready for L2 processing.
				
				// Try to get the appropriate exposure block for this event.
				// Note that since the frame was sitting on a queue, it's possible
				// that the appropriate xb has already lapsed. 
				ExposureBlock xb = current_xb;
				ExposureBlock prev_xb = previous_xb;
				if (prev_xb != null) {
					if (prev_xb.end_time < frame.acq_time) {
						// looks like we're done with the previous exposure block,
						// since this frame is too recent for it.
						// let's commit it and move on with the current_xb
						commitExposureBlock();
					}
					else {
						// aha, it seems this frame belongs to the old exposure block.
						// we'll use that one instead.
						xb = prev_xb;
					}
				}
				
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
				
				// only enforce the L2 threshold if we're not calibrating.
				int thresh = 0;
				if (current_state == DAQActivity.state.DATA) {
					thresh = xb.L2_thresh;
				}
				
				// First, build the event from the raw frame.
				RecoEvent event = reco.buildEvent(frame);
				
				// for now, everything "passes" L2 reco
				xb.L2_pass++;
				
				// Now pick out the L2 pixels and add them to the event.
				ArrayList<RecoPixel> pixels = reco.buildL2Pixels(frame, thresh);
				event.pixels = pixels;
				
				if (current_state == DAQActivity.state.DATA) {
					// keep track of the running totals for acquired
					// events/pixels over the app lifetime.
					totalEvents++;
					totalPixels += pixels.size();
				}
				
				// Finally, add the event to the proper exposure block.
				xb.addEvent(event);
				
				// If we got a bad frame, go back to calibration mode
				if (reco.good_quality == false
						&& fixed_threshold == false) {
					toCalibrationMode();
				}
				
				// At this point, we're done with the data; gc will deal with bytes array.
				L2counter++;
				
				// If we're calibrating, check if we've processed enough
				// frames to decide on the threshold(s) and go back to
				// data-taking mode.
				if (current_state == DAQActivity.state.CALIBRATION
						&& reco.event_count >= calibration_sample_frames) {
					toDataMode();
				}
			}
			running = false;
		}
	}
}
