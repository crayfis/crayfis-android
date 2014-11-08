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
import com.jjoe64.graphview.BarGraphView;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewDataInterface;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.ValueDependentColor;

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

/**
 * This is the main Activity of the app; this activity is started when the user
 * hits "Run" from the start screen. Here we manage the threads that acquire,
 * process, and upload the pixel data.
 */
public class DAQActivity extends ActionBarActivity implements Camera.PreviewCallback, SensorEventListener {

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication.AppBuild mAppBuild;

    // camera and display objects
	private Camera mCamera;
	private Visualization mDraw;
	private CameraPreviewView mPreview;

    // Widgets for giving feedback to the user.
    private StatusView mStatusView;
    private MessageView mMessageView;
    private AppBuildView mAppBuildView;

    private Timer mUiUpdateTimer;

    // ----8<--------------
    private GraphView mGraph;
    private GraphViewSeries mGraphSeries;
    private GraphViewSeriesStyle mGraphSeriesStyle;


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

    public enum display_mode {
        HIST, TIME
    }

    private display_mode current_mode;

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

	public void clickedMode() {
        CFLog.d(" clicked MODE current=" + current_mode.toString());
        // toggle modes
        if (current_mode == DAQActivity.display_mode.HIST) { current_mode = DAQActivity.display_mode.TIME; }
        else
        if (current_mode == DAQActivity.display_mode.TIME) { current_mode = DAQActivity.display_mode.HIST; }
        CFLog.d(" clicked MODE new="+current_mode.toString());

	}

    public GraphView.GraphViewData[] make_graph_data(int values[], boolean do_log, int start, int max_bin)
    {
        // show some empty bins
        if (max_bin<values.length)
            max_bin += 2;

        GraphView.GraphViewData gd[] = new GraphView.GraphViewData[max_bin];
        int which=start+1;
        for (int i=0;i<max_bin;i++)
        {
            if (which>=max_bin){ which=0;}
            if (do_log) {
                if (values[which] > 0)
                    gd[i] = new GraphView.GraphViewData(i, java.lang.Math.log(values[which]));
                else
                    gd[i] = new GraphView.GraphViewData(i, 0);
            } else
                gd[i] = new GraphView.GraphViewData(i, values[which]);
            which++;


        }
        return gd;
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

        if (current_mode==DAQActivity.display_mode.HIST)
		tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles.\n"+
                "This frame shows:\n\t Exposure: seconds of data-taking\n" +
                "\t Frames: number with a hot pixel\n" +
                "\t Candidates: number of pixels saved\n" +
                "\t Data blocks: groups of frames\n" +
                "\t Mode: STABILIZING, CALIBRATION or DATA\n" +
                "\t Scan rate: rate, frames-per-second\n" +
                "On the left is a histogram showing the distribution of observed pixel values. The large peak on the left is due to noise and light pollution. Candidate particles are in the longer tail on the right. \nFor more details:  "
				+ s);
        if (current_mode==DAQActivity.display_mode.TIME)
            tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles.\n"+
                    "This frame shows:\n\t Exposure: seconds of data-taking\n" +
                    "\t Frames: number with a hot pixel\n" +
                    "\t Candidates: number of pixels saved\n" +
                    "\t Data blocks: groups of frames\n" +
                    "\t Mode: STABILIZING, CALIBRATION or DATA\n" +
                    "\t Scan rate: rate, frames-per-second\n" +
                    "On the left is a time series showing the max pixel value found in each frame." +
                    "\nFor more details:  "
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

    private class ValueDependentColorX implements ValueDependentColor
    {
        @Override
        public int get (GraphViewDataInterface data){
        if (data.getY() == 0) return Color.BLACK;
        if (data.getX() < CONFIG.getL2Threshold())
            return Color.BLUE;
        return Color.RED;
        }
    }

    private class ValueDependentColorY implements ValueDependentColor
    {
        @Override
        public int get (GraphViewDataInterface data){
            if (data.getY() == 0) return Color.BLACK;
            if (data.getY() < CONFIG.getL2Threshold())
                return Color.BLUE;
            return Color.RED;
        }
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        current_mode = DAQActivity.display_mode.HIST;

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

        mStatusView = (StatusView) findViewById(R.id.status_view);
        mMessageView = (MessageView) findViewById(R.id.message_view);
        mAppBuildView = (AppBuildView) findViewById(R.id.app_build_view);

		// Used to visualize the results
		mDraw = new Visualization(this);
        mDraw.setZOrderOnTop(true);
        mDraw.getHolder().setFormat(PixelFormat.TRANSPARENT);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreviewView(this, this, true);

		context = getApplicationContext();

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);

        /// test graphing
        mGraph = new BarGraphView( this, "");

        int novals[] = new int[256];
        for (int i=0;i<256;i++) novals[i]=1;

        GraphViewSeriesStyle mGraphSeriesStyle = new GraphViewSeriesStyle();
        mGraphSeriesStyle.setValueDependentColor(new ValueDependentColorX());

        mGraphSeries = new GraphViewSeries("aaa",mGraphSeriesStyle,make_graph_data(novals, true, 0, 20));
        /*
        GraphViewSeries exampleSeries = new GraphViewSeries(new GraphView.GraphViewData[] {
                new GraphView.GraphViewData(1, 2.0d)
                , new GraphView.GraphViewData(2, 1.5d)
                , new GraphView.GraphViewData(3, 2.5d)
                , new GraphView.GraphViewData(4, 1.0d)
        });

        mGraph.addSeries(exampleSeries);
        */
        mGraph.setScalable(true);
        mGraph.addSeries(mGraphSeries);

        preview.addView(mGraph);

        preview.addView(mDraw);



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
            case R.id.menu_view_mode:
                clickedMode();
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
            mParticleReco = ParticleReco.getInstance(previewSize);
            l2thread = new L2Processor(this, previewSize);
            l2thread.setView(mDraw);
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

        // FIXME This is being called very time a frame is received, instead it should only be called when the mode changes.
        if (current_mode == DAQActivity.display_mode.HIST) {
            mGraphSeries.resetData(make_graph_data(mParticleReco.h_pixel.values, true,-1,mParticleReco.h_pixel.max_bin));
            //mGraph.getGraphViewStyle().setVerticalLabelsWidth(25);
            mGraph.setManualYAxisBounds(20., 0.);
            mGraph.setHorizontalLabels(new String[] {"","Pixel","values"});
            mGraphSeries.getStyle().setValueDependentColor(new ValueDependentColorX());



        }
        if (current_mode == DAQActivity.display_mode.TIME) {
            mGraphSeries.resetData(make_graph_data(mParticleReco.hist_max.values, false,mParticleReco.hist_max.current_time,mParticleReco.hist_max.values.length));
            //mGraph.getGraphViewStyle().setVerticalLabelsWidth(25);
            mGraph.setManualYAxisBounds(30., 0.);
            mGraph.setHorizontalLabels(new String[] {"","Time"," "," "});
            mGraphSeries.getStyle().setValueDependentColor(new ValueDependentColorY());
        }

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

	/**
	 * Draws on top of the video stream for visualizing computer vision results
	 */
	private class Visualization extends SurfaceView {

        Paint mypaint3;

        public Visualization(Activity context) {
            super(context);

            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int h = canvas.getHeight();
            int tsize = (h / 50);
            int yoffset = 2 * tsize;

            if (mypaint3 == null) {
                mypaint3 = new Paint();
                mypaint3.setStyle(android.graphics.Paint.Style.FILL);
                mypaint3.setColor(android.graphics.Color.GRAY);
                mypaint3.setTextSize(tsize);
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
                        mMessageView.setMessage(MessageView.Level.ERROR, "Network unavailable.");
                    } else {
                        String reason;
                        if (outputThread.valid_id) {
                            reason = "Server is overloaded.";
                        } else {
                            reason = "Invalid invite code.";
                        }
                        mMessageView.setMessage(MessageView.Level.WARNING, reason);
                    }
                } else if (L2busy > 0) {
                    mMessageView.setMessage(MessageView.Level.WARNING, L2busy + "(" + L2busy + ")");
                } else {
                    mMessageView.setMessage(null, null);
                }

                final StatusView.Status status = new StatusView.Status.Builder()
                        .setEventCount(mParticleReco.event_count)
                        .setFps((int) (fps_update_interval / (L1_fps_stop - L1_fps_start) * 1e3))
                        .setStabilizationCounter(stabilization_counter)
                        .setTotalEvents(l2thread.getTotalEvents())
                        .setTotalPixels(l2thread.getTotalPixels())
                        .setTime((int) (1.0e-3 * xbManager.getExposureTime()))
                        .build();
                mStatusView.setStatus(status);
                mAppBuildView.setAppBuild(((CFApplication) getApplication()).getBuildInformation());
            }
        };

        @Override
        public void run() {
            runOnUiThread(RUNNABLE);
        }
    };
}
