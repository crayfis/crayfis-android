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
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import java.util.Calendar;
import java.util.UUID;

import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.server.ServerCommand;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.L1Processor;
import io.crayfis.android.trigger.L2Processor;
import io.crayfis.android.util.CFLog;


/**
 * Extension of {@link android.app.Application}.
 */
public class CFApplication extends Application {

    public static final String ACTION_STATE_CHANGE = "state_change";
    public static final String STATE_CHANGE_PREVIOUS = "previous_state";
    public static final String STATE_CHANGE_NEW = "new_state";

    public static final String ACTION_FATAL_ERROR = "fatal_error";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";

    private int errorId = 2;

    private final long STABILIZATION_COUNTDOWN_TICK = 1000; // ms
    private final long STABILIZATION_DELAY = 10000; // ms

    private boolean mWaitingForStabilization = false;
    public int consecutiveIdles = 0;

    private CountDownTimer mStabilizationTimer = new CountDownTimer(STABILIZATION_DELAY, STABILIZATION_COUNTDOWN_TICK) {
        @Override
        public void onTick(long millisUntilFinished) {
            // TODO: ideally, we want this to display on tick in the Status pane
            CFLog.d("Time left: " + millisUntilFinished / 1000L);
        }

        @Override
        public void onFinish() {
            // see if we should quit the app here
            consecutiveIdles++;
            CFLog.d("" + consecutiveIdles + " consecutive IDLEs");

            if(consecutiveIdles >= 3) {
                handleUnresponsive();
            } else if(CFConfig.getInstance().getCameraSelectMode() != MODE_FACE_DOWN
                    || CFCamera.getInstance().isFlat()) {
                mWaitingForStabilization = false;
                setApplicationState(CFApplication.State.STABILIZATION);
            } else {
                // continue waiting
                this.start();
            }
        }

    };

    //private static final String SHARED_PREFS_NAME = "global";
    private static long mStartTimeNano;
    private static int mBatteryTemp;

    public static int badFlatEvents = 0;

    private State mApplicationState;

    private AppBuild mAppBuild;

    private static RenderScript mRS;

    @Override
    public void onCreate() {
        super.onCreate();

        Crashlytics.start(this);

        //SharedPreferences localPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final CFConfig config = CFConfig.getInstance();
        //localPrefs.registerOnSharedPreferenceChangeListener(config);
        defaultPrefs.registerOnSharedPreferenceChangeListener(config);
        // FIXME This is not needed when dependency injection is done, this can be handled in the constructor.
        //config.onSharedPreferenceChanged(localPrefs, null);
        config.onSharedPreferenceChanged(defaultPrefs, null);

        setApplicationState(State.INIT);

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

    public void onServerCommandRecieved(ServerCommand sc) {
        if (sc.shouldRecalibrate()) {
            // recieved a command from the server to enter calibration loop!
            setApplicationState(State.STABILIZATION);
        }
        if (sc.getResolution() != null) {
            // go to the recalibrate state, so that the camera can be setup with the new resolution.
            setApplicationState(State.RECONFIGURE);
        }
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

        if(applicationState != State.INIT) {
            final Intent intent = new Intent(ACTION_STATE_CHANGE);
            intent.putExtra(STATE_CHANGE_PREVIOUS, currentState);
            intent.putExtra(STATE_CHANGE_NEW, mApplicationState);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private void handleUnresponsive() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = ((status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                (status == BatteryManager.BATTERY_STATUS_FULL));


        if(!isCharging || !inAutostartWindow()) {
            stopService(new Intent(this, DAQService.class));
            finishAndQuit(R.string.quit_no_cameras);
        }
    }

    public boolean inAutostartWindow() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(!sharedPrefs.getBoolean("prefEnableAutoStart", false)) return false;

        int startAfter = Integer.parseInt(sharedPrefs.getString("prefStartAfter", "0"));
        int startBefore = Integer.parseInt(sharedPrefs.getString("prefStartBefore", "0"));
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);

        int b1 = (startAfter >= startBefore) ? 1 : 0;
        int b2 = (hour >= startAfter) ? 1 : 0;
        int b3 = (hour < startBefore) ? 1 : 0;

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
            Intent errorIntent = new Intent(ACTION_FATAL_ERROR);
            errorIntent.putExtra(EXTRA_ERROR_MESSAGE, dialogMessage);
            LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);
            stopService(new Intent(this, DAQService.class));
        } else {
            Toast.makeText(this, dialogMessage, Toast.LENGTH_LONG).show();
        }
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

    public static int getBatteryTemp() {
        return mBatteryTemp;
    }

    public static void setBatteryTemp(int newTemp) {
        mBatteryTemp = newTemp;
    }

    public static long getStartTimeNano() { return mStartTimeNano; }
    public static void setStartTimeNano(long startTimeNano) { mStartTimeNano = startTimeNano; }

    public static RenderScript getRenderScript() {
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
        final CFConfig CONFIG = CFConfig.getInstance();
        final CFCamera CAMERA = CFCamera.getInstance();
        CONFIG.setPrecalId(CAMERA.getCameraId(), mAppBuild.getRunId());
    }

    public void startStabilizationTimer() {
        setApplicationState(State.IDLE);
        mStabilizationTimer.start();
        mWaitingForStabilization = true;
    }

    public void killTimer() {
        mStabilizationTimer.cancel();
    }

    public boolean isWaitingForStabilization() {
        return mWaitingForStabilization;
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
        RECONFIGURE,
    }

    public static final int MODE_FACE_DOWN = 0;
    public static final int MODE_AUTO_DETECT = 1;
    public static final int MODE_BACK_LOCK = 2;
    public static final int MODE_FRONT_LOCK = 3;
}
