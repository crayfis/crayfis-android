package edu.uci.crayfis;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import edu.uci.crayfis.calibration.FrameHistory;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.precalibration.PreCalibrator;
import edu.uci.crayfis.camera.AcquisitionTime;
import edu.uci.crayfis.camera.CFCamera;
import edu.uci.crayfis.camera.CFLocation;
import edu.uci.crayfis.camera.CFSensor;
import edu.uci.crayfis.camera.RawCameraFrame;
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

public class DAQService extends Service implements Camera.PreviewCallback {



    ///////////////
    // Lifecycle //
    ///////////////

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication mApplication;
    private String upload_url;

    private NotificationCompat.Builder mNotificationBuilder;
    private final int FOREGROUND_ID = 1;

    private CFCamera mCFCamera;
    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    @Override
    public void onCreate() {
        super.onCreate();

        mApplication = (CFApplication)getApplication();
        context = getApplicationContext();

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

        // State changes

        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mBroadcastManager.registerReceiver(STATE_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
        mBroadcastManager.registerReceiver(CAMERA_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_CAMERA_CHANGE));


        mCFCamera = CFCamera.getInstance(context, BUILDER, this);
        mCFSensor = CFSensor.getInstance(context, BUILDER);
        mCFLocation = CFLocation.getInstance(context, BUILDER);


        // Frame Processing

        mL1Processor = new L1Processor(mApplication);
        mPreCal = PreCalibrator.getInstance(context);

        if (L1cal == null) {
            L1cal = L1Calibrator.getInstance();
        }
        if (frame_times == null) {
            frame_times = new FrameHistory<>(100);
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

        mNotificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_just_a)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_idle))
                .setContentIntent(resultPendingIntent);

        startForeground(FOREGROUND_ID, mNotificationBuilder.build());

        // tell service to restart if it gets killed
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        CFLog.i("DAQService Suspending!");

        DataCollectionFragment.getInstance().updateIdleStatus("");

        if(mApplication.getApplicationState() != CFApplication.State.IDLE) {
            mApplication.setApplicationState(CFApplication.State.IDLE);
        }

        mCFCamera.unregister();
        mCFSensor.unregister();
        mCFLocation.unregister();

        xbManager.flushCommittedBlocks(true);

        mBroadcastManager.unregisterReceiver(STATE_CHANGE_RECEIVER);
        mBroadcastManager.unregisterReceiver(CAMERA_CHANGE_RECEIVER);

        mHardwareCheckTimer.cancel();
        mApplication.killTimer();

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

            switch(current) {
                case STABILIZATION:
                    doStateTransitionStabilization(previous);
                    break;
                case PRECALIBRATION:
                    doStateTransitionPreCalibration(previous);
                    break;
                case CALIBRATION:
                    doStateTransitionCalibration(previous);
                    break;
                case DATA:
                    doStateTransitionData(previous);
                    break;
                case IDLE:
                    doStateTransitionIdle(previous);
                    break;
                case RECONFIGURE:
                    doStateTransitionReconfigure(previous);
                    break;
                default:
                    throw new IllegalFsmStateException(previous + " -> " + current);
            }
        }
    };

    private final BroadcastReceiver CAMERA_CHANGE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            xbManager.abortExposureBlock();
        }
    };


    /**
     * We go to stabilization mode in order to wait for the camera to settle down after a period of bad data.
     *
     * @param previousState Previous {@link edu.uci.crayfis.CFApplication.State}
     * @throws IllegalFsmStateException
     */
    private void doStateTransitionStabilization(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        // all the work here done in camera initialization
        switch (previousState) {
            case INIT:
            case RECONFIGURE:
            case IDLE:
                mApplication.changeCamera();
                BUILDER.setWeighted(false);
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
        mApplication.changeCamera();

        // notify user that we are no longer running
        mNotificationBuilder.setContentText(getString(R.string.notification_idle));
        startForeground(FOREGROUND_ID, mNotificationBuilder.build());

        switch(previousState) {
            case PRECALIBRATION:
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
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }
    }

    private void doStateTransitionPreCalibration(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        switch (previousState) {
            case CALIBRATION:
                xbManager.newExposureBlock(CFApplication.State.PRECALIBRATION);
                mPreCal.clear(mApplication.getCameraId());
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }
    }

    private void doStateTransitionCalibration(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        // first generate runconfig for specific camera
        if (run_config == null || mApplication.getCameraId() != run_config.getCameraId()) {
            generateRunConfig();
            UploadExposureService.submitRunConfig(context, run_config);
        }

        // notify user that camera is running
        mNotificationBuilder.setContentText(getString(R.string.notification_running));
        startForeground(FOREGROUND_ID, mNotificationBuilder.build());
        mApplication.consecutiveIdles = 0;

        if(mPreCal.dueForPreCalibration(mApplication.getCameraId())) {
            mApplication.setApplicationState(CFApplication.State.PRECALIBRATION);
            return;
        }
        switch (previousState) {
            case PRECALIBRATION:
            case STABILIZATION:
                L1cal.clear();
                frame_times.clear();
                CFApplication.badFlatEvents = 0;
                xbManager.newExposureBlock(CFApplication.State.CALIBRATION);
                BUILDER.setWeighted(true);
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
        mApplication.changeCamera();

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





    /////////////////////////
    // RunConfig generator //
    /////////////////////////

    DataProtos.RunConfig run_config;

    public void generateRunConfig() {
        long run_start_time = System.currentTimeMillis();
        long run_start_time_nano = System.nanoTime() - 1000000 * run_start_time; // reference start time to unix epoch

        CFApplication.setStartTimeNano(run_start_time_nano);

        DataProtos.RunConfig.Builder b = DataProtos.RunConfig.newBuilder();

        mApplication.generateAppBuild();
        CFApplication.AppBuild build = mApplication.getBuildInformation();

        final UUID runId = build.getRunId();
        b.setIdHi(runId.getMostSignificantBits());
        b.setIdLo(runId.getLeastSignificantBits());
        b.setCrayfisBuild(build.getBuildVersion());
        b.setStartTime(run_start_time);

        /* get a bunch of camera info */
        b.setCameraId(mApplication.getCameraId());
        b.setCameraParams(CFApplication.getCameraParams().flatten());




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

    private final RawCameraFrame.Builder BUILDER = new RawCameraFrame.Builder(this);
    private ExposureBlockManager xbManager;
    // helper that dispatches L1 inputs to be processed by the L1 trigger.
    private L1Processor mL1Processor = null;

    private L1Calibrator L1cal;
    private PreCalibrator mPreCal;

    private FrameHistory<Long> frame_times;
    private double target_L1_eff;

    // L1 hit threshold
    long starttime;

    // number of frames to wait between fps updates
    public final int fps_update_interval = 20;

    // store value of most recently calculated FPS
    private double last_fps = 0;

    private Context context;



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

            RawCameraFrame frame = BUILDER.setBytes(bytes)
                    .setAcquisitionTime(acq_time)
                    .setLocation(CFApplication.getLastKnownLocation())
                    .setExposureBlock(xb)
                    .build();

            // try to assign the frame to the current XB
            boolean assigned = xb.assignFrame(frame);

            // track the acquisition times for FPS calculation
            synchronized(frame_times) {
                frame_times.addValue(acq_time.Nano);
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
            // update new frames
            BUILDER.setBatteryTemp(newTemp);
            // update new exposure blocks
            CFApplication.setBatteryTemp(newTemp);
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
                    && !mApplication.isWaitingForStabilization()) {

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

    public class DAQBinder extends Binder {

        public void saveStatsBeforeSleeping() {
            mTimeBeforeSleeping = System.currentTimeMillis();
            mCountsBeforeSleeping = L2Processor.mL2Count;
        }

        public long getTimeWhileSleeping() {
            if(mTimeBeforeSleeping == 0) { return 0; } // on start of DAQService
            return System.currentTimeMillis() - mTimeBeforeSleeping;
        }

        public int getCountsWhileSleeping() {
            return L2Processor.mL2Count - mCountsBeforeSleeping;
        }

        public DataCollectionStatsView.Status getDataCollectionStatus() {
            final Camera.Size sz = CFApplication.getCameraSize();
            final DataCollectionStatsView.Status dstatus = new DataCollectionStatsView.Status.Builder()
                    .setTotalEvents(L2Processor.mL2Count)
                    .setTotalPixels((long)L1Processor.mL1CountData * sz.height * sz.width)
                    .setTotalFrames(L1Processor.mL1CountData)
                    .build();
            return dstatus;
        }

        public String getDevText() {

            if(mApplication.getApplicationState() != CFApplication.State.DATA) {
                updateFPS();
            }

            String devtxt = "@@ Developer View @@\n"
                    + "State: " + mApplication.getApplicationState() + "\n"
                    + "L2 trig: " + CONFIG.getL2Trigger() + "\n"
                    + "total frames - L1: " + L1Processor.mL1Count + " (L2: " + L2Processor.mL2Count + ")\n"
                    + "L1 Threshold:" + (CONFIG != null ? CONFIG.getL1Threshold() : -1) + (CONFIG.getTriggerLock() ? "*" : "")
                    + ", L2 Threshold:" + (CONFIG != null ? CONFIG.getL2Threshold() : -1) + "\n"
                    + "fps="+String.format("%1.2f",last_fps)+" target eff="+String.format("%1.2f",target_L1_eff)+"\n"
                    + "Exposure Blocks:" + (xbManager != null ? xbManager.getTotalXBs() : -1) + "\n"
                    + "Battery temp = " + String.format("%1.1f", batteryTemp/10.) + "C from "
                    +((System.currentTimeMillis()-last_battery_check_time)/1000)+"s ago.\n"
                    + "\n";

            devtxt += mCFCamera.getStatus();
            ExposureBlock xb = xbManager.getCurrentExposureBlock();
            devtxt += "xb avg: " + String.format("%1.4f",xb.getPixAverage()) + " max: " + String.format("%1.2f",xb.getPixMax()) + "\n";
            devtxt += "L1 hist = "+L1cal.getHistogram().toString()+"\n"
                    + "Upload server = " + upload_url + "\n";
            devtxt += mCFSensor.getStatus() + mCFLocation.getStatus();
            return devtxt;

        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }




}

