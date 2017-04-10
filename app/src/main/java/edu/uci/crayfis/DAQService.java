package edu.uci.crayfis;

import android.app.PendingIntent;
import android.app.Service;
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
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.renderscript.RenderScript;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.FileDescriptor;
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
import edu.uci.crayfis.widget.DataCollectionStatsView;

/**
 * Created by Jeff on 2/17/2017.
 */

public class DAQService extends Service implements Camera.PreviewCallback, SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {



    ///////////////
    // Lifecycle //
    ///////////////

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication mApplication;
    private CFApplication.AppBuild mAppBuild;
    private String upload_url;
    private final int FOREGROUND_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();

        mApplication = (CFApplication)getApplication();
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

        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mBroadcastManager.registerReceiver(STATE_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
        mBroadcastManager.registerReceiver(CAMERA_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_CAMERA_CHANGE));

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
        try {
            mLastLocationDeprecated = getLocationDeprecated();
        } catch (SecurityException se) {
            // permission revoked?
            Intent permissionIntent = new Intent(this, MainActivity.class);
            startActivity(permissionIntent);
            stopSelf();
        }
        newLocation(mLastLocationDeprecated, true);


        // Camera

        if (mCamera != null)
            throw new RuntimeException(
                    "Bug, camera should not be initialized already");


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

        if(mHardwareCheckTimer != null) {
            mHardwareCheckTimer.cancel();
        }
        mHardwareCheckTimer = new Timer();
        mHardwareCheckTimer.schedule(new BatteryUpdateTimerTask(), 0L, battery_check_wait);


        mApplication.setApplicationState(CFApplication.State.STABILIZATION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // notify user that CRAYFIS is running in background
        Intent restartIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(restartIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_just_a)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_message))
                .setContentIntent(resultPendingIntent);

        startForeground(FOREGROUND_ID, builder.build());

        // tell service to restart if it gets killed
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        CFLog.i("DAQService Suspending!");

        if(mApplication.getApplicationState() != CFApplication.State.IDLE) {
            mApplication.setApplicationState(CFApplication.State.IDLE);
        }

        mSensorManager.unregisterListener(this);
        mGoogleApiClient.disconnect();
        if(mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }
        unSetupCamera();
        xbManager.flushCommittedBlocks(true);

        if(ntpThread != null) {
            ntpThread.stopThread();
            ntpThread = null;
        }

        mHardwareCheckTimer.cancel();

        mBroadcastManager.unregisterReceiver(STATE_CHANGE_RECEIVER);
        mBroadcastManager.unregisterReceiver(CAMERA_CHANGE_RECEIVER);


        DataCollectionFragment.getInstance().updateIdleStatus("");
        CFLog.d("DAQService: stopped");
    }




    ///////////////////////////
    //  Handle state changes //
    ///////////////////////////

    LocalBroadcastManager mBroadcastManager;


    private final BroadcastReceiver STATE_CHANGE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final CFApplication.State previous = (CFApplication.State) intent.getSerializableExtra(CFApplication.STATE_CHANGE_PREVIOUS);
            final CFApplication.State current = (CFApplication.State) intent.getSerializableExtra(CFApplication.STATE_CHANGE_NEW);
            CFLog.d(DAQService.class.getSimpleName() + " state transition: " + previous + " -> " + current);

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
    };

    /**
     * We go to stabilization mode in order to wait for the camera to settle down after a period of bad data.
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
    private void doStateTransitionStabilization(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        mApplication.setCameraId(0);
        switch(previousState) {
            case INIT:
            case IDLE:
            case RECONFIGURE:
                break;

            // coming out of calibration and data should be the same.
            case CALIBRATION:
            case DATA:
                xbManager.abortExposureBlock();
                break;
            case STABILIZATION:
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
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
                DataCollectionFragment.getInstance().updateIdleStatus("No available cameras: waiting to retry");
                xbManager.newExposureBlock(CFApplication.State.IDLE);
                mStabilizationTimer.start();
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
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
                // generate runconfig for a specific camera
                if (run_config == null) {
                    generateRunConfig();
                    UploadExposureService.submitRunConfig(context, run_config);
                }
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
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
        setUpAndConfigureCamera(mApplication.getCameraId());

        // if we were idling, go back to that state.
        if (previousState == CFApplication.State.IDLE) {
            mApplication.setApplicationState(CFApplication.State.IDLE);
        } else {
            // otherwise, re-enter the calibration loop.
            mApplication.setApplicationState(CFApplication.State.STABILIZATION);
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
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }

    }


    private final BroadcastReceiver CAMERA_CHANGE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            CFLog.d("Changing cameras");
            unSetupCamera();
            setUpAndConfigureCamera(mApplication.getCameraId());
            if(mApplication.getCameraId() != 0) {
                // first choice failed
                xbManager.abortExposureBlock();
            } else {
                xbManager.newExposureBlock(mApplication.getApplicationState());
            }
        }
    };





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
    private Location mLastLocation;

    // Old API
    private LocationManager mLocationManager;
    private android.location.LocationListener mLocationListener;
    private Location mLastLocationDeprecated;

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
                if (!location_valid(CFApplication.getLastKnownLocation()))
                {
                    // use the deprecated info if it's the best we have
                    CFApplication.setLastKnownLocation(location);
                }
            }

        }
        //CFLog.d("## newLocation data "+location+" deprecated? "+deprecated+" -> current location is "+CFApplication.getLastKnownLocation());

    }

    public Location getLocationDeprecated() throws SecurityException
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
        mLocationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);

        // ask for updates from network and GPS
        try {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        } catch (RuntimeException e)
        { // some phones do not support
        }
        // get the last known coordinates for an initial value
        Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
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
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);


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
    private Camera.Parameters mParams;
    private Camera.Size previewSize;
    private SurfaceTexture mTexture;
    private final int N_CYCLE_BUFFERS = 7;

    /**
     * Sets up the camera if it is not already setup.
     */
    private synchronized void setUpAndConfigureCamera(int cameraId) {
        // Open and configure the camera
        CFLog.d("setUpAndConfigureCamera()");
        if(mCamera != null && CFApplication.getCameraSize() != null
                || cameraId > Camera.getNumberOfCameras()) { return; }
        try {
            mCamera = Camera.open(cameraId);
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

            // configure RenderScript if available
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                CFLog.i("Configuring RenderScript");

                RawCameraFrame.setCameraWithRenderScript(mCamera, cameraId, mRS);
            } else {
                RawCameraFrame.setCamera(mCamera, cameraId);
            }

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

    private ExposureBlockManager xbManager;
    // helper that dispatches L1 inputs to be processed by the L1 trigger.
    private L1Processor mL1Processor = null;
    // helper that dispatches L2 inputs to be processed by the L2 trigger.
    private L2Processor mL2Processor = null;

    private L1Calibrator L1cal = null;

    private FrameHistory<Long> frame_times;
    private double target_L1_eff;

    // L1 hit threshold
    long starttime;

    // number of frames to wait between fps updates
    public final int fps_update_interval = 20;

    // store value of most recently calculated FPS
    private double last_fps = 0;

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

    private Timer mHardwareCheckTimer;

    public final float battery_stop_threshold = 0.20f;
    public final float battery_start_threshold = 0.80f;
    public final int batteryOverheatTemp = 420;
    public final int batteryStartTemp = 350;

    private final long battery_check_wait = 10000; // ms
    private float batteryPct;
    private int batteryTemp;
    private boolean batteryOverheated = false;
    private long last_battery_check_time;

    private long stabilizationCountdownUpdateTick = 1000; // ms
    private long stabilizationDelay = 300000; // ms
    private CountDownTimer mStabilizationTimer = new CountDownTimer(stabilizationDelay, stabilizationCountdownUpdateTick) {
        @Override
        public void onTick(long millisUntilFinished) {
            CFLog.d("Time left: " + millisUntilFinished / 1000L);
        }

        @Override
        public void onFinish() {
            mApplication.setApplicationState(CFApplication.State.STABILIZATION);
        }

    };




    /**
     * Task that gets called during the UI update tick.
     */
    private final class BatteryUpdateTimerTask extends TimerTask {

        @Override
        public void run() {
            final DataCollectionFragment fragment = DataCollectionFragment.getInstance();
            last_battery_check_time = System.currentTimeMillis();

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            //
            // see if anything is wrong with the battery
            //

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
                fragment.updateIdleStatus("Cooling battery: " + String.format("%1.1f", newTemp / 10.) + "C");
            } else if (batteryPct < battery_start_threshold) {
                fragment.updateIdleStatus("Low battery: " + (int) (batteryPct * 100) + "%/" + (int) (battery_start_threshold * 100) + "%");
            }
            batteryTemp = newTemp;

            // go into idle mode if necessary
            if (mApplication.getApplicationState() != edu.uci.crayfis.CFApplication.State.IDLE) {
                if (batteryPct < battery_stop_threshold) {
                    CFLog.d(" Battery too low, going to IDLE mode.");
                    fragment.updateIdleStatus("Low battery: " + (int) (batteryPct * 100) + "%/" + (int) (battery_start_threshold * 100) + "%");
                    mApplication.setApplicationState(CFApplication.State.IDLE);
                } else if (batteryTemp > batteryOverheatTemp) {
                    CFLog.d(" Battery too hot, going to IDLE mode.");
                    fragment.updateIdleStatus("Cooling battery: " + String.format("%1.1f", batteryTemp / 10.) + "C");
                    mApplication.setApplicationState(CFApplication.State.IDLE);
                    batteryOverheated = true;
                }

            }
            // if we are in idle mode, restart if everything is okay
            else if (batteryPct >= battery_start_threshold && !batteryOverheated
                    && mApplication.getCameraId() < Camera.getNumberOfCameras()) {

                CFLog.d("Returning to stabilization");
                mApplication.setApplicationState(CFApplication.State.STABILIZATION);
            }

        }
    }





    /////////////////
    // UI Feedback //
    /////////////////

    private final IBinder mBinder = new DAQBinder();
    private long mTimeBeforeSleeping = 0;
    private int mCountsBeforeSleeping = 0;

    class DAQBinder extends Binder {

        void saveStatsBeforeSleeping() {
            mTimeBeforeSleeping = System.currentTimeMillis();
            mCountsBeforeSleeping = L2Processor.mL2Count;
        }

        long getTimeWhileSleeping() {
            if(mTimeBeforeSleeping == 0) { return 0; } // on start of DAQService
            return System.currentTimeMillis() - mTimeBeforeSleeping;
        }

        int getCountsWhileSleeping() {
            return L2Processor.mL2Count - mCountsBeforeSleeping;
        }

        void updateDataCollectionStatus() {
            final DataCollectionStatsView.Status dstatus = new DataCollectionStatsView.Status.Builder()
                    .setTotalEvents(L2Processor.mL2Count)
                    .setTotalPixels((long)L1Processor.mL1CountData * previewSize.height * previewSize.width)
                    .setTotalFrames(L1Processor.mL1CountData)
                    .build();
            CFApplication.setCollectionStatus(dstatus);
        }

        String getDevText() {

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
                devtxt += "Camera ID: " + mApplication.getCameraId() + ", Image dimensions = " + previewSize.width + "x" + previewSize.height
                        + " (" + (targetRes.name.isEmpty() ? targetRes : targetRes.name) +")\n";
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

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }



}

