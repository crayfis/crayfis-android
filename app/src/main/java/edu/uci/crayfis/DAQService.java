package edu.uci.crayfis;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

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
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.trigger.L1Processor;
import edu.uci.crayfis.trigger.L2Processor;
import edu.uci.crayfis.ui.DataCollectionFragment;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 2/17/2017.
 */

public class DAQService extends IntentService implements Camera.PreviewCallback, SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener, Runnable {


    private static final CFConfig CONFIG = CFConfig.getInstance();
    private static CFApplication mApplication;
    private CFApplication.AppBuild mAppBuild;
    private static String upload_url;


    /////////////////////////////////////////////
    // Basic elements of calling IntentService //
    /////////////////////////////////////////////

    private static final String ACTION_START = "edu.uci.crayfis.action.START";
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static boolean mRunService = true;
    private static boolean mIsRunning = false;

    public DAQService() {
        super("DAQService");
    }

    public static void startService(Context context) {

        // do nothing if DAQ is already running
        if(mIsRunning) { return; }

        CFLog.d("DAQService: starting");

        mRunService = true;
        Intent intent = new Intent(context, DAQService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    public static void endService() {

        CFLog.d("DAQService: ending");

        // FIXME: this is a bad way to do this
        mRunService = false;
        DataCollectionFragment.getInstance().updateIdleStatus("");

        // check to make sure the service has actually exited before proceeding
        while(mIsRunning) {
            try {
                Thread.sleep(100L);
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
        CFLog.d("DAQService: stopped");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                try {
                    startDAQ();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /* Spin up a BG thread where the camera data will delivered to */
    private void startBackgroundThread() {
        if (mBackgroundThread != null) return;
        mBackgroundThread = new HandlerThread("DAQ");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }




    ////////////////////////////
    // Initialize and run DAQ //
    ////////////////////////////

    private void startDAQ() throws InterruptedException {
        mIsRunning = true;

        // run DAQ in background thread
        startBackgroundThread();
        mBackgroundHandler.post(this);

        while(mRunService) {
            Thread.sleep(1000L);
        }

        // if we've made it here, tear down hardware

        CFLog.i("DAQService Suspending!");

        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.IDLE);

        mSensorManager.unregisterListener(this);
        mGoogleApiClient.disconnect();
        unSetupCamera();
        xbManager.flushCommittedBlocks(true);

        if(ntpThread != null) {
            ntpThread.stopThread();
            ntpThread = null;
        }

        mBatteryCheckTimer.cancel();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(STATE_CHANGE_RECEIVER);
        mIsRunning = false;
    }

    @Override
    public void run() {

        mApplication = (CFApplication) getApplication();
        context = getApplicationContext();
        mRS = RenderScript.create(context);

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
        upload_url = upload_proto + server_address+":"+server_port+upload_uri;

        mAppBuild = mApplication.getBuildInformation();


        // State changes

        LocalBroadcastManager.getInstance(context).registerReceiver(STATE_CHANGE_RECEIVER,
                new IntentFilter(CFApplication.ACTION_STATE_CHANGE));


        // Sensors

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        Sensor sens = gravSensor;
        if (sens == null) {
            sens = accelSensor;
        }

        mSensorManager.registerListener(this, sens, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);


        // Location

        newLocation(new Location("BLANK"), false);
        buildGoogleApiClient();
        mGoogleApiClient.connect();
        // backup location if Google play isn't working or installed
        mLastLocationDeprecated = getLocationDeprecated();
        newLocation(mLastLocationDeprecated, true);


        // Camera

        if (mCamera != null)
            throw new RuntimeException(
                    "Bug, camera should not be initialized already");

        setUpAndConfigureCamera();


        // Frame Processing

        mL2Processor = new L2Processor(mApplication);
        mL1Processor = new L1Processor(mApplication);
        mL1Processor.setL2Processor(mL2Processor);

        if (L1cal == null) {
            L1cal = L1Calibrator.getInstance();
        }
        if (frame_times == null) {
            frame_times = new FrameHistory<>(100);
        }

        SntpClient.getInstance();
        if (ntpThread == null) {
            ntpThread = new SntpUpdateThread();
            ntpThread.start();
        }

        xbManager = ExposureBlockManager.getInstance(this);


        // System check

        starttime = System.currentTimeMillis();
        last_battery_check_time = starttime;
        batteryPct = -1; // indicate no data yet
        batteryTemp = -1;

        if(mBatteryCheckTimer != null) {
            mBatteryCheckTimer.cancel();
        }
        mBatteryCheckTimer = new Timer();
        mBatteryCheckTimer.schedule(new BatteryUpdateTimerTask(), 0L, battery_check_wait);



        ((CFApplication) getApplication()).setApplicationState(CFApplication.State.STABILIZATION);

        // once all the hardware is set up and the output manager is running,
        // we can generate and commit the runconfig
        if (run_config == null) {
            generateRunConfig();
            UploadExposureService.submitRunConfig(context, run_config);
        }
    }





    ///////////////////////////
    //  Handle state changes //
    ///////////////////////////


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
                // so the calibration will be clean.
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

    /**
     * Set up for the transition to {@link edu.uci.crayfis.CFApplication.State#DATA}
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
    private void doStateTransitionData(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {

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





    /////////////
    // Sensors //
    /////////////


    SensorManager mSensorManager;
    private Sensor gravSensor = null;
    private Sensor accelSensor = null;
    private Sensor magSensor = null;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] orientation = new float[3];

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




    //////////////
    // Location //
    //////////////

    // New API
    private GoogleApiClient mGoogleApiClient;
    private static Location mLastLocation;
    private LocationRequest mLocationRequest;

    // Old API
    private android.location.LocationListener mLocationListener = null;
    private static Location mLastLocationDeprecated;

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        CFLog.d("Build API client:"+mGoogleApiClient);
    }

    private boolean location_valid(Location location)
    {

        return (location != null
                && java.lang.Math.abs(location.getLongitude())>0.1
                && java.lang.Math.abs(location.getLatitude())>0.1);

    }

    private void newLocation(Location location, boolean deprecated)
    {

        if (!deprecated)
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




    //////////////////////////
    // Camera configuration //
    //////////////////////////

    // camera and display objects
    private Camera mCamera;
    private static Camera.Parameters mParams;
    private static Camera.Size previewSize;
    private SurfaceTexture mTexture;
    private static final int N_CYCLE_BUFFERS = 10;

    /**
     * Sets up the camera if it is not already setup.
     */
    private synchronized void setUpAndConfigureCamera() {
        // Open and configure the camera
        CFLog.d("setUpAndConfigureCamera()");
        if(mCamera != null) return;
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            //userErrorMessage(getResources().getString(R.string.camera_error),true);

        }
        CFLog.d("Camera opened camera="+mCamera);
        try {

            Camera.Parameters param= mCamera.getParameters();

            previewSize = CONFIG.getTargetResolution().getClosestSize(mCamera);
            CFLog.i("selected preview size="+previewSize);

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
                CFLog.i("Configuring RenderScript");
                Type.Builder tb = new Type.Builder(mRS, Element.createPixel(mRS, Element.DataType.UNSIGNED_8, Element.DataKind.PIXEL_YUV));
                tb.setX(previewSize.width)
                        .setY(previewSize.height)
                        .setYuvFormat(param.getPreviewFormat());
                Type type = tb.create();
                ScriptIntrinsicHistogram script = ScriptIntrinsicHistogram.create(mRS, Element.U8(mRS));
                RawCameraFrame.useRenderScript(mRS, type, script);
            }

        } catch (Exception e) {
            //userErrorMessage(getResources().getString(R.string.camera_error),true);

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
            RawCameraFrame.setCamera(mCamera, previewSize);
            mCamera.startPreview();
        }  catch (Exception e) {
            //userErrorMessage(getResources().getString(R.string.camera_error),true);
        }
    }

    private synchronized void unSetupCamera() {

        if(mCamera == null) return;

        // stop the camera preview and all processing
        mCamera.stopPreview();
        if (N_CYCLE_BUFFERS>0) {
            mCamera.setPreviewCallbackWithBuffer(null);
        } else {
            mCamera.setPreviewCallback(null);
        }
        mTexture = null;
        mParams = null;
        mCamera.release();
        mCamera = null;

        // clear out any (old) buffers
        //l2thread.clearQueue();
        // FIXME: should we abort any AsyncTasks in the L1/L2 queue that haven't executed yet?

        CFLog.d(" DAQActivity: unsetup camera");
    }







    /////////////////////////
    // RunConfig generator //
    /////////////////////////

    DataProtos.RunConfig run_config = null;

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




    //////////////////////
    // Frame processing //
    //////////////////////

    private static ExposureBlockManager xbManager;
    // helper that dispatches L1 inputs to be processed by the L1 trigger.
    private L1Processor mL1Processor = null;
    // helper that dispatches L2 inputs to be processed by the L2 trigger.
    private L2Processor mL2Processor = null;

    private L1Calibrator L1cal = null;

    private static FrameHistory<Long> frame_times;
    private static double target_L1_eff;

    // L1 hit threshold
    long starttime;

    // number of frames to wait between fps updates
    public static final int fps_update_interval = 20;

    // store value of most recently calculated FPS
    private static double last_fps = 0;

    // Thread for NTP updates
    private SntpUpdateThread ntpThread;

    private Context context;
    private RenderScript mRS;



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
            RawCameraFrame frame = new RawCameraFrame(bytes, acq_time);
            frame.setLocation(CFApplication.getLastKnownLocation());
            frame.setOrientation(orientation);
            frame.setBatteryTemp(batteryTemp);

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

    private static double updateFPS() {
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

    public int calculateL1Threshold() {
        double fps = updateFPS();
        if (fps == 0) {
            CFLog.w("Warning! Got 0 fps in threshold calculation.");
        }
        target_L1_eff = ((double) CONFIG.getTargetEventsPerMinute()) / 60.0 / getFPS();
        return L1cal.findL1Threshold(target_L1_eff);
    }








    ///////////////////////////
    // Routine system checks //
    ///////////////////////////

    private Timer mBatteryCheckTimer;


    public final float battery_stop_threshold = 0.20f;
    public final float battery_start_threshold = 0.80f;
    public final int batteryOverheatTemp = 450;
    public final int batteryStartTemp = 380;

    private final long battery_check_wait = 10000; // ms
    private static float batteryPct;
    private static int batteryTemp;
    private static boolean batteryOverheated = false;
    private static long last_battery_check_time;



    /**
     * Task that gets called during the UI update tick.
     */
    private final class BatteryUpdateTimerTask extends TimerTask {

        @Override
        public void run() {
            final CFApplication application = (CFApplication) getApplication();
            last_battery_check_time = System.currentTimeMillis();

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryPct = level / (float) scale;
            // if overheated, see if battery temp is still falling
            int newTemp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            xbManager.updateBatteryTemp(newTemp);
            CFLog.d("Temperature change: " + batteryTemp + "->" + newTemp);
            if (batteryOverheated) {
                // see if temp has stabilized below overheat threshold or has reached a sufficiently low temp
                batteryOverheated = (newTemp <= batteryTemp && newTemp > batteryStartTemp) || newTemp > batteryOverheatTemp;
                DataCollectionFragment.getInstance().updateIdleStatus("Cooling battery: " + String.format("%1.1f", newTemp / 10.) + "C");
            } else {
                // if not in IDLE mode for overheat, must be for low battery
                DataCollectionFragment.getInstance().updateIdleStatus("Low battery: " + (int) (batteryPct * 100) + "%/" + (int) (battery_start_threshold * 100) + "%");
            }
            batteryTemp = newTemp;

            if (application.getApplicationState() != edu.uci.crayfis.CFApplication.State.IDLE) {
                if (batteryPct < battery_stop_threshold) {
                    CFLog.d(" Battery too low, going to IDLE mode.");
                    DataCollectionFragment.getInstance().updateIdleStatus("Low battery: " + (int) (batteryPct * 100) + "%/" + (int) (battery_start_threshold * 100) + "%");
                    application.setApplicationState(CFApplication.State.IDLE);
                } else if (batteryTemp > batteryOverheatTemp) {
                    CFLog.d(" Battery too hot, going to IDLE mode.");
                    DataCollectionFragment.getInstance().updateIdleStatus("Cooling battery: " + String.format("%1.1f", batteryTemp / 10.) + "C");
                    application.setApplicationState(CFApplication.State.IDLE);
                    batteryOverheated = true;
                }

            }

            if (application.getApplicationState() == edu.uci.crayfis.CFApplication.State.IDLE
                    && batteryPct > battery_start_threshold && !batteryOverheated) {
                CFLog.d(" Battery ok now, returning to run mode.");
                setUpAndConfigureCamera();

                application.setApplicationState(CFApplication.State.STABILIZATION);
            }
        }
    }






    /////////////////
    // UI Feedback //
    /////////////////

    public static String getDevText() {

        if(mApplication.getApplicationState() != CFApplication.State.DATA) {
            updateFPS();
        }

        String devtxt = "@@ Developer View @@\n"
                + "State: " + mApplication.getApplicationState() + "\n"
                + "L2 trig: " + CONFIG.getL2Trigger() + "\n"
                + "total frames - L1: " + L1Processor.mL1Count + " (L2: " + L2Processor.mL2Count + ")\n"
                + "L1 Threshold:" + (CONFIG != null ? CONFIG.getL1Threshold() : -1) + (CONFIG.getTriggerLock() ? "*" : "") + "\n"
                + "fps="+String.format("%1.2f",last_fps)+" target eff="+String.format("%1.2f",target_L1_eff)+"\n"
                + "Exposure Blocks:" + (xbManager != null ? xbManager.getTotalXBs() : -1) + "\n"
                + "Battery power pct = "+(int)(100*batteryPct)+"%, temp = "
                + String.format("%1.1f", batteryTemp/10.) + "C from "+((System.currentTimeMillis()-last_battery_check_time)/1000)+"s ago.\n"
                + "\n";

        if (previewSize != null) {
            ResolutionSpec targetRes = CONFIG.getTargetResolution();
            devtxt += "Image dimensions = " + previewSize.width + "x" + previewSize.height + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) +")\n";
        }
        ExposureBlock xb = xbManager.getCurrentExposureBlock();
        devtxt += "xb avg: " + String.format("%1.4f",xb.getPixAverage()) + " max: " + String.format("%1.2f",xb.getPixMax()) + "\n";
        devtxt += "L1 hist = "+L1Calibrator.getHistogram().toString()+"\n"
                + "Upload server = " + upload_url + "\n"
                + (mLastLocation != null ? "Current google location: (long=" + mLastLocation.getLongitude() + ", lat=" + mLastLocation.getLatitude() + ") accuracy = " + mLastLocation.getAccuracy() + " provider = " + mLastLocation.getProvider() + " time=" + mLastLocation.getTime() : "") + "\n"
                + (mLastLocationDeprecated != null ? "Current android location: (long=" + mLastLocationDeprecated.getLongitude() + ", lat=" + mLastLocationDeprecated.getLatitude() + ") accuracy = " + mLastLocationDeprecated.getAccuracy() + " provider = " + mLastLocationDeprecated.getProvider() + " time=" + mLastLocationDeprecated.getTime() : "") + "\n"
                + (CFApplication.getLastKnownLocation() != null ? " Official location = (long="+CFApplication.getLastKnownLocation().getLongitude()+" lat="+CFApplication.getLastKnownLocation().getLatitude() : "") + "\n";

        return devtxt;

    }


}

