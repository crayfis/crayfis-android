package io.crayfis.android.main;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;

import org.opencv.android.OpenCVLoader;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.trigger.L0.L0Processor;
import io.crayfis.android.trigger.L1.L1Calibrator;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.exception.IllegalFsmStateException;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.ui.navdrawer.status.DataCollectionStatsView;

/**
 * Created by Jeff on 2/17/2017.
 */

public class DAQService extends Service {

    static {
        if(OpenCVLoader.initDebug()) {
            CFLog.d("OpenCV installed");
        } else {
            CFLog.d("OpenCV not installed");
        }
    }


    ///////////////
    // Lifecycle //
    ///////////////

    private final CFConfig CONFIG = CFConfig.getInstance();
    private CFApplication mApplication;

    private NotificationCompat.Builder mNotificationBuilder;
    private final int FOREGROUND_ID = 1;

    private CFCamera mCFCamera;

    private ExposureBlockManager xbManager;

    @Override
    public void onCreate() {
        super.onCreate();

        CFLog.d("DAQService onCreate()");
        mApplication = (CFApplication)getApplication();
        mBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // restart if DAQService is dead
        if(mApplication.getApplicationState() == CFApplication.State.FINISHED) {

            mBroadcastManager.registerReceiver(STATE_CHANGE_RECEIVER, new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
            mApplication.setApplicationState(CFApplication.State.INIT);
        }

        // tell service to restart if it gets killed
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        CFLog.i("DAQService Suspending!");

        if(mApplication.getApplicationState() != CFApplication.State.FINISHED) {
            mApplication.setApplicationState(CFApplication.State.FINISHED);
        }

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
            CFLog.i(DAQService.class.getSimpleName() + " state transition: " + previous + " -> " + current);

            switch(current) {
                case INIT:
                    doStateTransitionInitialization(previous);
                    break;
                case SURVEY:
                    doStateTransitionSurvey(previous);
                    break;
                case PRECALIBRATION:
                    doStateTransitionPrecalibration(previous);
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
                case FINISHED:
                    doStateTransitionFinished(previous);
                    break;
                default:
                    throw new IllegalFsmStateException(previous + " -> " + current);
            }
        }
    };

    /**
     * In INIT mode, we set up the cameras, trigger, etc.
     */
    private void doStateTransitionInitialization(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {

        switch (previousState) {
            case FINISHED:

                // Notifications

                Intent restartIntent = new Intent(this, MainActivity.class);
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
                stackBuilder.addParentStack(MainActivity.class);
                stackBuilder.addNextIntent(restartIntent);
                PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                if(mNotificationBuilder == null) {
                    mNotificationBuilder = new NotificationCompat.Builder(this)
                            .setSmallIcon(R.drawable.ic_just_a)
                            .setContentTitle(getString(R.string.notification_title))
                            .setContentText(getString(R.string.notification_idle))
                            .setContentIntent(resultPendingIntent);
                }

                startForeground(FOREGROUND_ID, mNotificationBuilder.build());

                xbManager = ExposureBlockManager.getInstance();
                xbManager.register(mApplication);
                xbManager.newExposureBlock(CFApplication.State.INIT);

                // start camera

                mCFCamera = CFCamera.getInstance();
                mCFCamera.register(mApplication);
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }
    }

    /**
     * We go to survey mode in order to wait for the camera to settle down after a period of bad data.
     *
     * @param previousState Previous {@link CFApplication.State}
     * @throws IllegalFsmStateException
     */
    private void doStateTransitionSurvey(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {

        CONFIG.setL1Threshold(255);
        switch (previousState) {
            case IDLE:
                mCFCamera.changeCamera();
            case INIT:
                xbManager.newExposureBlock(CFApplication.State.SURVEY);
                mCFCamera.getFrameBuilder().setWeights(null);
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }

    }

    /**
     * This mode is basically just used to cleanly close out any current exposure blocks.
     * For example, we transition here when the phone is locked or the app is suspended.
     *
     * @param previousState Previous {@link CFApplication.State}
     * @throws IllegalFsmStateException
     */
    private void doStateTransitionIdle(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {

        // notify user that we are no longer running
        mNotificationBuilder.setContentText(getString(R.string.notification_idle));
        startForeground(FOREGROUND_ID, mNotificationBuilder.build());

        // schedule a timer to check battery
        Timer idleTimer = new Timer();
        TimerTask idleTimerTask = new TimerTask() {
            @Override
            public void run() {
                if(mApplication.checkBatteryStats() == Boolean.TRUE) {
                    this.cancel();
                }
            }
        };
        idleTimer.schedule(idleTimerTask,
                CONFIG.getExposureBlockPeriod() * 1000,
                CONFIG.getExposureBlockPeriod() * 1000);

        switch(previousState) {
            case INIT:
                // starting with low battery, but finish initialization first
                break;
            case SURVEY:
            case PRECALIBRATION:
            case CALIBRATION:
            case DATA:
                // for calibration or data, mark the block as aborted
                mCFCamera.changeCamera();
                xbManager.abortExposureBlock();
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }
    }

    private void doStateTransitionPrecalibration(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        switch (previousState) {
            case CALIBRATION:
                xbManager.newExposureBlock(CFApplication.State.PRECALIBRATION);
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }
    }

    private void doStateTransitionCalibration(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {
        // first generate runconfig for specific camera
        if (run_config == null || mCFCamera.getCameraId() != run_config.getCameraId()) {
            generateRunConfig();
            UploadExposureService.submitRunConfig(this, run_config);
        }

        // notify user that camera is running
        mNotificationBuilder.setContentText(getString(R.string.notification_running));
        startForeground(FOREGROUND_ID, mNotificationBuilder.build());
        mApplication.consecutiveIdles = 0;

        if(PreCalibrator.dueForPreCalibration(mCFCamera.getCameraId())) {
            mApplication.setApplicationState(CFApplication.State.PRECALIBRATION);
            return;
        }

        switch (previousState) {
            case SURVEY:
            case PRECALIBRATION:
                mCFCamera.badFlatEvents = 0;
                PreCalibrator.updateWeights(mApplication.getRenderScript(), CFCamera.getInstance().getCameraId());
                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }

        xbManager.newExposureBlock(CFApplication.State.CALIBRATION);
    }

    /**
     * Set up for the transition to {@link CFApplication.State#DATA}
     *
     * @param previousState Previous {@link CFApplication.State}
     * @throws IllegalFsmStateException
     */
    private void doStateTransitionData(@NonNull final CFApplication.State previousState) throws IllegalFsmStateException {

        switch (previousState) {
            case CALIBRATION:
                // Finally, set the state and start a new xb
                xbManager.newExposureBlock(CFApplication.State.DATA);

                break;
            default:
                throw new IllegalFsmStateException(previousState + " -> " + mApplication.getApplicationState());
        }

    }

    private void doStateTransitionFinished(CFApplication.State previous) {
        switch (previous) {
            case INIT:
            case SURVEY:
            case CALIBRATION:
            case PRECALIBRATION:
            case DATA:
            case IDLE:
                xbManager.newExposureBlock(CFApplication.State.FINISHED);
                xbManager.flushCommittedBlocks();
                mCFCamera.unregister();
                xbManager.unregister();

                mApplication.killTimer();

                mBroadcastManager.unregisterReceiver(STATE_CHANGE_RECEIVER);
                stopSelf();
                break;
            default:
                throw new IllegalFsmStateException(previous + " -> " + mApplication.getApplicationState());
        }
    }



    /////////////////////////
    // RunConfig generator //
    /////////////////////////

    DataProtos.RunConfig run_config;

    private void generateRunConfig() {

        DataProtos.RunConfig.Builder b = DataProtos.RunConfig.newBuilder();
        b.setStartTime(System.currentTimeMillis());

        mApplication.generateAppBuild();
        CFApplication.AppBuild build = mApplication.getBuildInformation();

        final UUID runId = build.getRunId();
        b.setIdHi(runId.getMostSignificantBits());
        b.setIdLo(runId.getLeastSignificantBits());
        b.setCrayfisBuild(build.getBuildVersion());

        /* get a bunch of camera info */
        b.setCameraId(mCFCamera.getCameraId());
        b.setCameraParams(mCFCamera.getParams());



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


    /////////////////
    // UI Feedback //
    /////////////////

    private final IBinder mBinder = new DAQBinder();
    private long mTimeBeforeSleeping = 0;
    private int mCountsBeforeSleeping = 0;

    public class DAQBinder extends Binder {

        public void saveStatsBeforeSleeping() {
            mTimeBeforeSleeping = System.currentTimeMillis();
            mCountsBeforeSleeping = L2Processor.L2Count;
        }

        public long getTimeWhileSleeping() {
            if(mTimeBeforeSleeping == 0) { return 0; } // on start of DAQService
            return System.currentTimeMillis() - mTimeBeforeSleeping;
        }

        public int getCountsWhileSleeping() {
            return L2Processor.L2Count - mCountsBeforeSleeping;
        }

        public DataCollectionStatsView.Status getDataCollectionStatus() {
            final int area = mCFCamera.getResX() * mCFCamera.getResY();
            return new DataCollectionStatsView.Status.Builder()
                    .setTotalEvents(L2Processor.L2Count)
                    .setTotalPixels((long)L1Processor.L1CountData * area)
                    .setTotalFrames(L1Processor.L1CountData)
                    .build();
        }

        public String getDevText() {

            double lastFPS = mCFCamera.getFPS();
            double targetL1Rate = CONFIG.getTargetEventsPerMinute() / 60.0 / lastFPS;

            String devtxt = "@@ Developer View @@\n"
                    + "State: " + mApplication.getApplicationState() + "\n"
                    + "total frames - L1: " + L0Processor.L0Count.intValue() + " (L2: " + L2Processor.L2Count + ")\n"
                    + "target eff=" +String.format("%1.2f", targetL1Rate)+ "\n"
                    + "L1 pass rate=" + String.format("%1.2f", L2Processor.getPassRateFPM())
                    + ", target=" + String.format("%1.2f",CONFIG.getTargetEventsPerMinute())+"\n"
                    + "Exposure Blocks:" + (xbManager != null ? xbManager.getTotalXBs() : -1) + "\n"
                    + "Battery temp = " + String.format("%1.1f", mApplication.getBatteryTemp()/10.) + "C\n"
                    + "\n";

            devtxt += mCFCamera.getStatus() + "\n";

            devtxt += "L0 trig: " + CONFIG.getL0Trigger() + "\n"
                    + "Qual trig: " + CONFIG.getQualTrigger() + "\n"
                    + "Precal trig: " + CONFIG.getPrecalTrigger() + "\n"
                    + "L1 trig: " + CONFIG.getL1Trigger() + "\n"
                    + "L2 trig: " + CONFIG.getL2Trigger() + "\n";

            ExposureBlock xb = xbManager.getCurrentExposureBlock();
            if(xb != null) {
                devtxt += xb.underflow_hist.toString();
            }
            devtxt += "L1 hist = "+ L1Calibrator.getHistogram().toString()+"\n";
            return devtxt;

        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}

