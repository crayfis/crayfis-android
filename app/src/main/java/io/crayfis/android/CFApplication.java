package io.crayfis.android;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.renderscript.RenderScript;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.crashlytics.android.Crashlytics;

import java.util.Calendar;
import java.util.UUID;

import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.L1Processor;
import io.crayfis.android.trigger.L2Processor;
import io.crayfis.android.ui.DataCollectionFragment;
import io.crayfis.android.util.CFLog;
import io.fabric.sdk.android.Fabric;


/**
 * Extension of {@link android.app.Application}.
 */
public class CFApplication extends Application {

    public static final String ACTION_STATE_CHANGE = "state_change";
    public static final String STATE_CHANGE_PREVIOUS = "previous_state";
    public static final String STATE_CHANGE_NEW = "new_state";

    public static final String ACTION_ERROR = "daqservice_error";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";
    public static final String EXTRA_IS_FATAL = "fatal_error";

    private int errorId = 2;

    private final long STABILIZATION_COUNTDOWN_TICK = 1000; // ms
    private final long STABILIZATION_DELAY = 10000; // ms

    private boolean mWaitingForStabilization = false;
    public int consecutiveIdles = 0;

    private final CFConfig CONFIG = CFConfig.getInstance();

    private CountDownTimer mStabilizationTimer = new CountDownTimer(STABILIZATION_DELAY, STABILIZATION_COUNTDOWN_TICK) {
        @Override
        public void onTick(long millisUntilFinished) {
            CFLog.d(Long.toString(millisUntilFinished));
            int secondsLeft = (int) Math.round(millisUntilFinished/1000.);
            DataCollectionFragment.updateIdleStatus(String.format(getResources().getString(R.string.idle_camera), secondsLeft));
        }

        @Override
        public void onFinish() {
            // see if we should quit the app here
            if(mApplicationState != State.IDLE) return;
            consecutiveIdles++;
            CFLog.d("" + consecutiveIdles + " consecutive IDLEs");

            if(consecutiveIdles >= 3) {
                handleUnresponsive();
                return;
            }
            if(CFConfig.getInstance().getCameraSelectMode() != MODE_FACE_DOWN
                    || CFCamera.getInstance().isFlat()) {
                mWaitingForStabilization = false;
                setApplicationState(CFApplication.State.STABILIZATION);
            } else {
                // continue waiting
                userErrorMessage(R.string.warning_facedown, false);
                this.start();
            }
        }

    };

    //private static final String SHARED_PREFS_NAME = "global";
    private static long mStartTimeNano;
    private int mBatteryTemp = -1;
    private final int mBatteryStartTemp = 350;
    private final float mBatteryStartPct = .80f;
    private boolean mBatteryLow;
    private boolean mBatteryOverheated;


    private State mApplicationState;

    private AppBuild mAppBuild;

    private RenderScript mRS;

    @Override
    public void onCreate() {
        super.onCreate();

        Fabric.with(this, new Crashlytics());

        //SharedPreferences localPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final CFConfig config = CFConfig.getInstance();
        //localPrefs.registerOnSharedPreferenceChangeListener(config);
        defaultPrefs.registerOnSharedPreferenceChangeListener(config);
        // FIXME This is not needed when dependency injection is done, this can be handled in the constructor.
        //config.onSharedPreferenceChanged(localPrefs, null);
        config.onSharedPreferenceChanged(defaultPrefs, null);

        setApplicationState(State.FINISHED);

        // DEBUG
        final Intent intent = new Intent(this, UploadExposureService.class);
        startService(intent);
        mRS = RenderScript.create(this);
    }

    /**
     * Save the current preferences.
     */
    public void savePreferences() {
        //final SharedPreferences localPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        final SharedPreferences localPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        CFConfig.getInstance().save(localPrefs);
    }

    /**
     * Get the current application state.
     *
     * @return {@link io.crayfis.android.CFApplication.State}
     */
    public State getApplicationState() {
        return mApplicationState;
    }

    /**
     * Sets the application state.  Upon being set, a local broadcast with the action {@link #ACTION_STATE_CHANGE}
     * is sent with the state transition information bundled with the intent.  The previous state may
     * be {@code null} where as the new state should never be null.
     *
     * @param applicationState New {@link State}
     * @see #ACTION_STATE_CHANGE
     * @see #STATE_CHANGE_PREVIOUS
     * @see #STATE_CHANGE_NEW
     */
    public void setApplicationState(State applicationState) {
        final State currentState = mApplicationState;
        mApplicationState = applicationState;

        final Intent intent = new Intent(ACTION_STATE_CHANGE);
        intent.putExtra(STATE_CHANGE_PREVIOUS, currentState);
        intent.putExtra(STATE_CHANGE_NEW, mApplicationState);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

    private void handleUnresponsive() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = ((status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                (status == BatteryManager.BATTERY_STATUS_FULL));

        if(!isCharging || !inAutostartWindow()) {
            finishAndQuit(R.string.quit_no_cameras);
        }
    }

    public boolean inAutostartWindow() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(!sharedPrefs.getBoolean(getString(R.string.prefEnableAutoStart), false)) return false;

        int startAfter = sharedPrefs.getInt(getString(R.string.prefStartAfter), 0);
        int startBefore = sharedPrefs.getInt(getString(R.string.prefStartBefore), 0);
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int timeInMinutes = 60 * hour + minute;

        int b1 = (startAfter >= startBefore) ? 1 : 0;
        int b2 = (timeInMinutes >= startAfter) ? 1 : 0;
        int b3 = (timeInMinutes < startBefore) ? 1 : 0;

        return b1 + b2 + b3 >= 2;
    }

    public void userErrorMessage(@StringRes int id, boolean quit) {
        userErrorMessage(id, quit, false);
    }

    public void finishAndQuit(@StringRes int id) {
        userErrorMessage(id, true, true);
    }

    private void userErrorMessage(@StringRes int id, boolean quit, boolean safeExit) {

        String dialogMessage = getString(id);
        Intent errorIntent = new Intent(ACTION_ERROR);
        errorIntent.putExtra(EXTRA_ERROR_MESSAGE, dialogMessage);
        if(quit) {
            CFLog.e("Error: " + dialogMessage);
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

            if(sharedPrefs.getBoolean(getString(R.string.pref_enable_notif), true)) {

                String title;
                String text;
                if(safeExit) {
                    title = getString(R.string.notification_quit);
                    text = String.format(getString(R.string.notification_stats),
                            L1Processor.mL1CountData, L2Processor.mL2Count);
                } else {
                    title = getString(R.string.notification_error);
                    text = dialogMessage;
                }
                Notification notification = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_just_a)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(null)
                        .build();

                NotificationManager notificationManager
                        = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(errorId, notification);
                errorId++;
            }

            // make sure to kill activity if open
            errorIntent.putExtra(EXTRA_IS_FATAL, true);
            setApplicationState(State.FINISHED);
        } else {
            errorIntent.putExtra(EXTRA_IS_FATAL, false);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);
    }

    /**
     * Get the {@link io.crayfis.android.CFApplication.AppBuild} for this instance.
     *
     * @return {@link io.crayfis.android.CFApplication.AppBuild}
     */
    public AppBuild getBuildInformation() {
        if (mAppBuild == null) {
            generateAppBuild();
        }

        return mAppBuild;
    }

    public synchronized void generateAppBuild() {
        try {
            mAppBuild = new AppBuild(this);
        } catch (PackageManager.NameNotFoundException e) {
            // Seriously, this should never happen but does warrant a RuntimeException if it does.
            Crashlytics.logException(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds the battery temperature and charge, then switches to IDLE mode if the battery
     * has poor health or to STABILIZATION if the battery returns to health
     *
     * @return true if in good health, false otherwise
     */
    public Boolean checkBatteryStats() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        // get battery updates
        if(batteryStatus == null) return null;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float) scale;
        int newTemp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

        // check for low battery
        if (mBatteryLow) {
            mBatteryLow = batteryPct < mBatteryStartPct;
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String batteryPref = prefs.getString(getString(R.string.prefBatteryStop), "20%");
            batteryPref = batteryPref.substring(0, batteryPref.length()-1);
            double batteryStopPct = Integer.parseInt(batteryPref)/100.;
            mBatteryLow = batteryPct < batteryStopPct;
        }

        // check temperature for overheat
        if (mBatteryOverheated) {
            // see if temp has stabilized below overheat threshold or has reached a sufficiently low temp
            mBatteryOverheated = (newTemp <= mBatteryTemp && newTemp > mBatteryStartTemp) || newTemp > CONFIG.getBatteryOverheatTemp();
            DataCollectionFragment.updateIdleStatus(String.format(getResources().getString(R.string.idle_cooling),
                    newTemp / 10.));
        } else {
            mBatteryOverheated = newTemp > CONFIG.getBatteryOverheatTemp();
        }

        if(mBatteryTemp != newTemp) {
            CFLog.i("Temperature change: " + mBatteryTemp + "->" + newTemp);
            mBatteryTemp = newTemp;
        }

        if(mBatteryLow) {
            DataCollectionFragment.updateIdleStatus(String.format(getResources().getString(R.string.idle_low),
                    (int) (batteryPct * 100), (int) (mBatteryStartPct * 100)));
        } else if(mBatteryOverheated) {
            DataCollectionFragment.updateIdleStatus(String.format(getResources().getString(R.string.idle_cooling),
                    newTemp / 10.));
        }


        // go into idle mode if necessary
        if (mApplicationState != CFApplication.State.IDLE && mApplicationState != State.FINISHED
                && (mBatteryLow || mBatteryOverheated)) {
            setApplicationState(CFApplication.State.IDLE);
        }

        // if we are in idle mode, restart if everything is okay
        else if (mApplicationState == CFApplication.State.IDLE
                && !mBatteryLow && !mBatteryOverheated && !mWaitingForStabilization) {

            setApplicationState(CFApplication.State.STABILIZATION);
        }

        return !mBatteryLow && !mBatteryOverheated;

    }

    public int getBatteryTemp() {
        return mBatteryTemp;
    }

    public static long getStartTimeNano() { return mStartTimeNano; }
    public static void setStartTimeNano(long startTimeNano) { mStartTimeNano = startTimeNano; }

    public RenderScript getRenderScript() {
        return mRS;
    }

    private boolean useWifiOnly() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPrefs.getBoolean("prefWifiOnly", true);
    }

    // Some utilities for determining the network state
    private NetworkInfo getNetworkInfo() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    // Check if there is *any* connectivity
    private boolean isConnected() {
        NetworkInfo info = getNetworkInfo();
        return (info != null && info.isConnected());
    }

    // Check if we're connected to WiFi
    private boolean isConnectedWifi() {
        NetworkInfo info = getNetworkInfo();
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }

    /**
     * Checks if there is an available network connection available based on the configuration.
     *
     * <ul>
     *     <li>{@code true} if WiFi is enabled.</li>
     *     <li>{@code true} if WiFi only is disabled and the cellular data connection is available.</li>
     * </ul>
     * @return Whether the network is available or not.
     */
    public boolean isNetworkAvailable() {
        return (!useWifiOnly() && isConnected()) || isConnectedWifi();
    }

    /**
     * Wrapper class containing information relavent to this instance of the application.
     */
    public static final class AppBuild {

        private final String mBuildVersion;
        private final int mVersionCode;
        private final String mDeviceId;
        private final UUID mRunId;

        private AppBuild(@NonNull final Context context) throws PackageManager.NameNotFoundException {
            final PackageManager manager = context.getPackageManager();
            final String packageName = context.getPackageName();
            final PackageInfo info = manager.getPackageInfo(packageName, 0);

            mBuildVersion = info.versionName;
            mVersionCode = info.versionCode;
            mDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            mRunId = UUID.randomUUID();
        }

        /**
         * Get the build version.
         *
         * @return String
         */
        public String getBuildVersion() {
            return mBuildVersion;
        }

        /**
         * Get the build version code.
         *
         * @return int
         */
        public int getVersionCode() {
            return mVersionCode;
        }

        /**
         * Get the device id.
         *
         * @return String
         */
        public String getDeviceId() {
            return mDeviceId;
        }

        /**
         * Get the run id.
         *
         * @return UUID
         */
        public UUID getRunId() {
            return mRunId;
        }
    }

    public void setNewestPrecalUUID() {
        CONFIG.setPrecalId(CFCamera.getInstance().getCameraId(), mAppBuild.getRunId());
    }

    public void startStabilizationTimer() {
        setApplicationState(State.IDLE);
        mStabilizationTimer.start();
        mWaitingForStabilization = true;
    }

    public void killTimer() {
        mStabilizationTimer.cancel();
    }

    /**
     * Application state values.
     */
    public enum State {
        INIT,
        PRECALIBRATION,
        CALIBRATION,
        DATA,
        STABILIZATION,
        IDLE,
        FINISHED
    }

    public static final int MODE_FACE_DOWN = 0;
    public static final int MODE_AUTO_DETECT = 1;
    public static final int MODE_BACK_LOCK = 2;
    public static final int MODE_FRONT_LOCK = 3;
}