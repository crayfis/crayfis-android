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

import android.support.v4.view.ViewPager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;


import org.json.JSONObject;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import edu.uci.crayfis.camera.CameraPreviewView;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exception.IllegalFsmStateException;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.exposure.ExposureBlockManager;
import edu.uci.crayfis.particle.ParticleReco;
import edu.uci.crayfis.server.ServerCommand;
import edu.uci.crayfis.util.CFLog;
import edu.uci.crayfis.widget.AppBuildView;
import edu.uci.crayfis.widget.MessageView;
import edu.uci.crayfis.widget.StatusView;
import edu.uci.crayfis.widget.DataView;


/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends ActionBarActivity implements Camera.PreviewCallback, SensorEventListener {

    private ViewPager _mViewPager;
    private ViewPagerAdapter _adapter;


    private static LayoutData mLayoutData;
    private static LayoutHist mLayoutHist;
    private static LayoutTime mLayoutTime;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication.AppBuild mAppBuild;

    // camera and display objects
	private Camera mCamera;
	private CameraPreviewView mPreview;


    private Timer mUiUpdateTimer;

    // ----8<--------------



	// WakeLock to prevent the phone from sleeping during DAQ
	PowerManager.WakeLock wl;

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

	private long L1counter = 0;


	private long calibration_start;

	// counter for stabilization mode
	private int stabilization_counter;

	// L1 hit threshold
	long starttime;

    // number of frames to wait between fps updates
	public static final float fps_update_interval = 30;

	private long L1_fps_start = 0;
	private long L1_fps_stop = 0;

	// Thread where image data is processed for L2
	private L2Processor l2thread;

	// thread to handle output of committed data
	private OutputManager outputThread;

	// class to find particles in frames
	private ParticleReco mParticleReco;

	Context context;

	public void clickedSettings() {

		Intent i = new Intent(this, UserSettingActivity.class);
		startActivity(i);
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

	public void clickedAbout() {

		final SpannableString s = new SpannableString(
				"crayfis.ps.uci.edu/about");

		final TextView tx1 = new TextView(this);

        if (_mViewPager.getCurrentItem()==0)
  		  tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles.\n"+
                "This view shows:\n\t Time: seconds of data-taking\n" +
                "\t Mode: STABILIZING, CALIBRATION or DATA\n" +
                "\t Rate: scan rate, frames-per-second\n" +
                  " Swipe right for more views.\n For more details: "
				+ s);
        if (_mViewPager.getCurrentItem()==1)
            tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles.\n"+
                    "This view shows:\n" +
                    "\t Frames: number with a hot pixel\n" +
                    "\t Candidates: number of pixels saved\n" +
                    "On the bottom is a histogram showing the distribution of observed pixel values. The large peak on the left is due to noise and light pollution. Candidate particles are in the longer tail on the right. \nSwipe left or right for different views\nFor more details:  "
                            + s);
        if (_mViewPager.getCurrentItem()==2)
            tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles.\n"+
                    "This view shows a time series showing the max pixel value found in each frame." +
                    "\nSwipe left for more views\nFor more details:  "
                    + s);

		tx1.setAutoLinkMask(RESULT_OK);
		tx1.setMovementMethod(LinkMovementMethod.getInstance());
        tx1.setTextColor(Color.WHITE);
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
        CFApplication.setLastKnownLocation(location);
	}

	private void doStateTransitionCalibration(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
		// The *only* valid way to get into calibration mode
		// is after stabilizaton.
		switch (previousState) {
            case STABILIZATION:
                mParticleReco.reset();
                calibration_start = System.currentTimeMillis();
                xbManager.newExposureBlock();
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + ((CFApplication) getApplication()).getApplicationState());
        }
	}

    /**
     * Set up for the transition to {@link edu.uci.crayfis.CFApplication.State#DATA}
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
	private void doStateTransitionData(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
		switch (previousState) {
            case INIT:
                l2thread.setFixedThreshold(true);
                xbManager.newExposureBlock();

                break;
            case CALIBRATION:
                int new_thresh;
                long calibration_time = l2thread.getCalibrationStop() - calibration_start;
                int target_events = (int) (CONFIG.getTargetEventsPerMinute() * calibration_time * 1e-3 / 60.0);

                CFLog.i("Calibration: Processed " + mParticleReco.event_count + " frames in " + (int) (calibration_time * 1e-3) + " s; target events = " + target_events);

                // build the calibration result object
                DataProtos.CalibrationResult.Builder cal = DataProtos.CalibrationResult.newBuilder();
                // (ugh, why can't primitive arrays be Iterable??)
                for (int v : mParticleReco.h_pixel.values) {
                    cal.addHistPixel(v);
                }
                for (int v : mParticleReco.h_l2pixel.values) {
                    cal.addHistL2Pixel(v);
                }
                for (int v : mParticleReco.h_maxpixel.values) {
                    cal.addHistMaxpixel(v);
                }
                for (int v : mParticleReco.h_numpixel.values) {
                    cal.addHistNumpixel(v);
                }
                // and commit it to the output stream
                CFLog.i("DAQActivity Committing new calibration result.");
                outputThread.commitCalibrationResult(cal.build());

                // update the thresholds
                new_thresh = mParticleReco.calculateThresholdByEvents(target_events);
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
                l2thread.setFixedThreshold(false);
                l2thread.clearQueue();
                stabilization_counter = 0;
                xbManager.newExposureBlock();
                break;

            // coming out of calibration and data should be the same.
            case CALIBRATION:
            case DATA:
                l2thread.clearQueue();
                stabilization_counter = 0;
                xbManager.abortExposureBlock();
                break;
            case STABILIZATION:
                // just reset the counter.
                stabilization_counter = 0;

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
		DataProtos.RunConfig.Builder b = DataProtos.RunConfig.newBuilder();

        final UUID runId = mAppBuild.getRunId();
		b.setIdHi(runId.getMostSignificantBits());
		b.setIdLo(runId.getLeastSignificantBits());
		b.setCrayfisBuild(mAppBuild.getBuildVersion());
		b.setStartTime(System.currentTimeMillis());
		b.setCameraParams(mCamera.getParameters().flatten());

		run_config = b.build();
	}



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);


        mAppBuild = ((CFApplication) getApplication()).getBuildInformation();

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

		/*
		// clear saved settings (for debug purposes)
		SharedPreferences.Editor editor = localPrefs.edit();
		editor.clear();
		editor.commit();
		*/

		setContentView(R.layout.video);

        _mViewPager = (ViewPager) findViewById(R.id.viewPager);
        _adapter = new ViewPagerAdapter(getApplicationContext(),getSupportFragmentManager());
        _mViewPager.setAdapter(_adapter);
        _mViewPager.setCurrentItem(0);


		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreviewView(this, this, true);

        CFLog.d("DAQActivity: Camera preview view is "+mPreview);

		context = getApplicationContext();

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);




        L1counter = 0;

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
        LocationManager locationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);

		// ask for updates from network and GPS
		// locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
		// 0, 0, locationListener);
		// locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
		// 0, 0, locationListener);

		// get the last known coordinates for an initial value
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (null == location) {
			location = new Location("BLANK");
		}
        CFApplication.setLastKnownLocation(location);

		xbManager = ExposureBlockManager.getInstance(this);

		// Spin up the output and image processing threads:
		outputThread = OutputManager.getInstance(this);

        //Prevents thread from thread duplication if it's already initialized
		if (outputThread.getState() == Thread.State.NEW) {
            outputThread.start();
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(STATE_CHANGE_RECEIVER,
                new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
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

		// request to stop the OutputManager. It will automatically
		// try to upload any remaining data before dying.
		outputThread.stopThread();
		outputThread = null;

        LocalBroadcastManager.getInstance(this).unregisterReceiver(STATE_CHANGE_RECEIVER);
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

        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.STABILIZATION);

		// configure the camera parameters and start it acquiring frames.
		setUpAndConfigureCamera();

		// once all the hardware is set up and the output manager is running,
		// we can generate and commit the runconfig
		if (run_config == null) {
			generateRunConfig();
			outputThread.commitRunConfig(run_config);
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

		if (wl.isHeld())
			wl.release();

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

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
	 * Sets up the camera if it is not already setup.
	 */
	private void setUpAndConfigureCamera() {
		// Open and configure the camera
		mCamera = Camera.open();

		Camera.Parameters param = mCamera.getParameters();

		CFLog.d("params: Camera params are" + param.flatten());

		// Select the preview size closest to 320x240
		// Smaller images are recommended because some computer vision
		// operations are very expensive
		List<Camera.Size> sizes = param.getSupportedPreviewSizes();
		// previewSize = sizes.get(closest(sizes,640,480));
		previewSize = sizes.get(closest(sizes, targetPreviewWidth, targetPreviewHeight));
		// Camera.Size previewSize = sizes.get(closest(sizes,176,144));

        if (mParticleReco == null) {
            // if we don't already have a particleReco object setup,
            // do that now that we know the camera size.
            mParticleReco = ParticleReco.getInstance();
            mParticleReco.setPreviewSize(previewSize);
            l2thread = new L2Processor(this, previewSize);
            l2thread.start();
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

        mPreview.setCamera(mCamera);
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

        final CFApplication application = (CFApplication) getApplication();
		if (application.getApplicationState() == CFApplication.State.CALIBRATION) {
			// In calbration mode, there's no need for L1 trigger; just go straight to L2
			boolean queue_accept = l2thread.submitToQueue(new RawCameraFrame(bytes, acq_time, xb, orientation));

			if (! queue_accept) {
				// oops! the queue is full... this frame will be dropped.
				CFLog.e("DAQActivity Could not add frame to L2 Queue!");
				L2busy++;
			}

			return;
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

		// prescale
		// Jodi - removed L1prescale as it never changed.
		if (L1counter % 1 == 0) {
			// make sure there's room on the queue
			if (l2thread.getRemainingCapacity() > 0) {
				// check if we pass the L1 threshold
				boolean pass = false;
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
                mParticleReco.hist_max.new_data(max);
				if (pass) {
					xb.L1_pass++;

					// this frame has passed the L1 threshold, put it on the
					// L2 processing queue.
					boolean queue_accept = l2thread.submitToQueue(new RawCameraFrame(bytes, acq_time, xb, orientation));

					if (! queue_accept) {
						// oops! the queue is full... this frame will be dropped.
						CFLog.e("DAQActivity Could not add frame to L2 Queue!");
						L2busy++;
					}
				}
			}
			else {
				// no room on the L2 queue! We'll have to skip this frame.
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

            try {
                if (current == CFApplication.State.DATA) {
                    doStateTransitionData(previous);
                } else if (current == CFApplication.State.STABILIZATION) {
                    doStateTransitionStabilization(previous);
                } else if (current == CFApplication.State.IDLE) {
                    doStateTransitionIdle(previous);
                } else if (current == CFApplication.State.CALIBRATION) {
                    doStateTransitionCalibration(previous);
                }
            } catch (IllegalStateException e) {
                // Make some noise, instead of silently catching errors for now.
                CFLog.e("Illegal state transition! " + previous + " -> " + current);
                Crashlytics.logException(e);
                final AlertDialog dialog = CFUtil.newFatalErrorDialog(DAQActivity.this);
                if (dialog != null) {
                    dialog.show();
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
                if (!outputThread.canUpload()) {
                    if (outputThread.permit_upload) {
                        mLayoutData.mMessageView.setMessage(MessageView.Level.ERROR, "Network unavailable.");
                    } else {
                        String reason;
                        if (outputThread.valid_id) {
                            reason = "Server is overloaded.";
                        } else {
                            reason = "Invalid invite code.";
                        }
                        mLayoutData.mMessageView.setMessage(MessageView.Level.WARNING, reason);
                    }
                } else if (L2busy > 0) {
                    final String ignoredFrames = getResources().getQuantityString(R.plurals.total_frames, L2busy, L2busy);
                    mLayoutData.mMessageView.setMessage(MessageView.Level.WARNING, "Ignored " + ignoredFrames);
                } else {
                    mLayoutData.mMessageView.setMessage(null, null);
                }

                final StatusView.Status status = new StatusView.Status.Builder()
                        .setEventCount(mParticleReco.event_count)
                        .setFps((int) (fps_update_interval / (L1_fps_stop - L1_fps_start) * 1e3))
                        .setStabilizationCounter(stabilization_counter)
                        .setTotalEvents(l2thread.getTotalEvents())
                        .setTotalPixels(l2thread.getTotalPixels())
                        .setTime((int) (1.0e-3 * xbManager.getExposureTime()))
                        .build();

                final DataView.Status dstatus = new DataView.Status.Builder()
                        .setTotalEvents(l2thread.getTotalEvents())
                        .setTotalPixels(l2thread.getTotalPixels())
                        .build();

                final CFApplication application = (CFApplication) getApplication();

                if (application.getApplicationState() == CFApplication.State.STABILIZATION)
                {
                    mLayoutData.mProgressWheel.setText("Stabilizing");
                    mLayoutData.mProgressWheel.setTextColor(Color.RED);
                    mLayoutData.mProgressWheel.setBarColor(Color.RED);

                    mLayoutData.mProgressWheel.spin();
                }


                if (application.getApplicationState() == CFApplication.State.CALIBRATION) {
                    mLayoutData.mProgressWheel.setText("Calibrating");
                    mLayoutData.mProgressWheel.setTextColor(Color.YELLOW);
                    mLayoutData.mProgressWheel.setBarColor(Color.YELLOW);

                    int events = mParticleReco.event_count;
                    int needev = CONFIG.getCalibrationSampleFrames();
                    float frac = events/((float)1.0*needev);
                    int progress = (int)(360*frac);
                    CFLog.d("Calibration: l2="+events+" need="+needev+" frac="+frac+" prog="+progress);
                    mLayoutData.mProgressWheel.setProgress( progress );
                     }
                if (application.getApplicationState() == CFApplication.State.DATA) {
                    mLayoutData.mProgressWheel.setText("Data");
                    mLayoutData.mProgressWheel.setTextColor(Color.GREEN);
                    mLayoutData.mProgressWheel.setBarColor(Color.GREEN);

                    // solid circle
                    mLayoutData.mProgressWheel.setProgress( 360);

                }

                mLayoutData.mStatusView.setStatus(status);
                mLayoutHist.mDataView.setStatus(dstatus);

                mLayoutData.mAppBuildView.setAppBuild(((CFApplication) getApplication()).getBuildInformation());


                mLayoutHist.updateData();
                mLayoutTime.updateData();


            }
        };

        @Override
        public void run() {
            runOnUiThread(RUNNABLE);
        }
    };
}
