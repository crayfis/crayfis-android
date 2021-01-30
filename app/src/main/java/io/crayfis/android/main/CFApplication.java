package io.crayfis.android.main;

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
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Calendar;
import java.util.UUID;

import io.crayfis.android.R;
import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.ui.navdrawer.status.LayoutStatus;
import io.crayfis.android.util.CFLog;


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

    private static final int ERROR_ID = 2; // different from FOREGROUND_ID to allow second notification

    private static final long SURVEY_COUNTDOWN_TICK = 1000; // ms
    private static final long SURVEY_DELAY = 10000; // ms

    private boolean mWaitingForSurvey = false;
    public int consecutiveIdles = 0;

    private final CFConfig CONFIG = CFConfig.getInstance();

    private CountDownTimer mSurveyTimer = new CountDownTimer(SURVEY_DELAY, SURVEY_COUNTDOWN_TICK) {
        @Override
        public void onTick(long millisUntilFinished) {
            CFLog.d(Long.toString(millisUntilFinished));
            int secondsLeft = (int) Math.round(millisUntilFinished/1000.);
            LayoutStatus.updateIdleStatus(String.format(getResources().getString(R.string.idle_camera), secondsLeft));
        }

        @Override
        public void onFinish() {
            // see if we should quit the app here
            if(mApplicationState != State.IDLE) return;
            consecutiveIdles++;
            CFLog.d("" + consecutiveIdles + " consecutive IDLEs");

            if(consecutiveIdles >= 3) {
                if(handleUnresponsive()) return;
            }
            if(CFConfig.getInstance().getQualTrigger().getName().equals("facedown")
                    || DAQManager.getInstance().isPhoneFlat()) {
                mWaitingForSurvey = false;
                setApplicationState(CFApplication.State.SURVEY);
            } else {
                // continue waiting
                userErrorMessage(false, R.string.warning_facedown);
                this.start();
            }
        }

    };

    //private static final String SHARED_PREFS_NAME = "global";
    private int mBatteryTemp = -1;
    private static final int BATTERY_START_TEMP = 350;
    private static final float BATTERY_START_PCT = .80f;
    private boolean mBatteryLow;
    private boolean mBatteryOverheated;


    private State mApplicationState;

    private AppBuild mAppBuild;

    private RenderScript mRS;

    @Override
    public void onCreate() {
        super.onCreate();

        //SharedPreferences localPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final CFConfig config = CFConfig.getInstance();
        //localPrefs.registerOnSharedPreferenceChangeListener(config);
        defaultPrefs.registerOnSharedPreferenceChangeListener(config);
        // FIXME This is not needed when dependency injection is done, this can be handled in the constructor.
        //config.onSharedPreferenceChanged(localPrefs, null);
        config.onSharedPreferenceChanged(defaultPrefs, null);

        setApplicationState(State.FINISHED);
        mBatteryLow = false;
        mBatteryOverheated = false;

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
     * @return {@link CFApplication.State}
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

    private boolean handleUnresponsive() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = ((status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                (status == BatteryManager.BATTERY_STATUS_FULL));

        if(!isCharging || !inAutostartWindow()) {
            finishAndQuit(R.string.quit_no_cameras);
            return true;
        }
        return false;
    }

    public boolean inAutostartWindow() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(!sharedPrefs.getBoolean(getString(R.string.prefEnableAutoStart), false)) return false;

        try {
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

        } catch(ClassCastException e) {
            // FIXME: this is a fix from an old version.  Deprecate.
            sharedPrefs.edit()
                    .putInt(getString(R.string.prefStartAfter), 0)
                    .putInt(getString(R.string.prefStartBefore), 480)
                    .apply();
            
            return false;
        }
    }

    public void userErrorMessage(boolean quit, @StringRes int id, Object... formatArgs) {
        userErrorMessage(quit, false, id, formatArgs);
    }

    public void finishAndQuit(@StringRes int id, Object... formatArgs) {
        userErrorMessage(true, true, id, formatArgs);
    }

    private void userErrorMessage(boolean quit, boolean safeExit, @StringRes int id, Object... formatArgs) {

        String dialogMessage = getString(id, formatArgs);
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
                            L1Processor.L1CountData, L2Processor.L2Count);
                } else {
                    title = getString(R.string.notification_error);
                    text = dialogMessage;
                }
                Notification notification = new NotificationCompat.Builder(this, DAQService.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_just_a)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(null)
                        .build();

                NotificationManager notificationManager
                        = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(ERROR_ID, notification);
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
     * Get the {@link CFApplication.AppBuild} for this instance.
     *
     * @return {@link CFApplication.AppBuild}
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
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds the battery temperature and charge, then switches to IDLE mode if the battery
     * has poor health or to SURVEY if the battery returns to health
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
            mBatteryLow = batteryPct < BATTERY_START_PCT;
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
            mBatteryOverheated = (newTemp <= mBatteryTemp && newTemp > BATTERY_START_TEMP) || newTemp > CONFIG.getBatteryOverheatTemp();
            LayoutStatus.updateIdleStatus(String.format(getResources().getString(R.string.idle_cooling),
                    newTemp / 10.));
        } else {
            mBatteryOverheated = newTemp > CONFIG.getBatteryOverheatTemp();
        }

        if(mBatteryTemp != newTemp) {
            CFLog.i("Temperature change: " + mBatteryTemp + "->" + newTemp);
            mBatteryTemp = newTemp;
        }

        if(mBatteryLow) {
            LayoutStatus.updateIdleStatus(String.format(getResources().getString(R.string.idle_low),
                    (int) (batteryPct * 100), (int) (BATTERY_START_PCT * 100)));
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if(status != BatteryManager.BATTERY_STATUS_CHARGING && mApplicationState != State.FINISHED) {
                finishAndQuit(R.string.quit_low);
            }
        } else if(mBatteryOverheated) {
            LayoutStatus.updateIdleStatus(String.format(getResources().getString(R.string.idle_cooling),
                    newTemp / 10.));
        }


        // go into idle mode if necessary
        if (mApplicationState != CFApplication.State.IDLE && mApplicationState != State.FINISHED
                && (mBatteryLow || mBatteryOverheated)) {
            setApplicationState(CFApplication.State.IDLE);
        }

        // if we are in idle mode, restart if everything is okay
        else if (mApplicationState == CFApplication.State.IDLE
                && !mBatteryLow && !mBatteryOverheated && !mWaitingForSurvey) {

            setApplicationState(CFApplication.State.SURVEY);
        }

        return !mBatteryLow && !mBatteryOverheated;

    }

    public int getBatteryTemp() {
        return mBatteryTemp;
    }

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

    public void startSurveyTimer() {
        setApplicationState(State.IDLE);
        mSurveyTimer.start();
        mWaitingForSurvey = true;
    }

    public void killTimer() {
        mSurveyTimer.cancel();
    }

    /**
     * Application state values.
     */
    public enum State {
        INIT,
        PRECALIBRATION,
        CALIBRATION,
        DATA,
        SURVEY,
        IDLE,
        FINISHED
    }
}
