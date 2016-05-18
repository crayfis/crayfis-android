package edu.uci.crayfis;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.crashlytics.android.Crashlytics;

import java.util.UUID;

import edu.uci.crayfis.server.ServerCommand;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.widget.DataCollectionStatsView;

/**
 * Extension of {@link android.app.Application}.
 */
public class CFApplication extends Application {

    public static final String ACTION_STATE_CHANGE = "state_change";
    public static final String STATE_CHANGE_PREVIOUS = "previous_state";
    public static final String STATE_CHANGE_NEW = "new_state";
    // TODO: This should be a configurable value in the preferences.
    public static final int SLEEP_TIMEOUT_MS = 60000;

    //private static final String SHARED_PREFS_NAME = "global";
    private static Location mLastKnownLocation;
    private static long mStartTimeNano;
    private static Camera.Size mCameraSize;

    // FIXME This is a hack.
    // The way things are coupled in DAQActivity, exposing fields that can create the Status is worse than this.
    private static DataCollectionStatsView.Status sStatus;

    private State mApplicationState;

    private AppBuild mAppBuild;

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
     * @return {@link edu.uci.crayfis.CFApplication.State}
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

    /**
     * Get the {@link edu.uci.crayfis.CFApplication.AppBuild} for this instance.
     *
     * @return {@link edu.uci.crayfis.CFApplication.AppBuild}
     */
    public synchronized AppBuild getBuildInformation() {
        if (mAppBuild == null) {
            try {
                mAppBuild = new AppBuild(this);
            } catch (PackageManager.NameNotFoundException e) {
                // Seriously, this should never happen but does warrant a RuntimeException if it does.
                Crashlytics.logException(e);
                throw new RuntimeException(e);
            }
        }

        return mAppBuild;
    }

    public static Location getLastKnownLocation() {
        return mLastKnownLocation;
    }

    public static void setLastKnownLocation(Location lastKnownLocation) {
        mLastKnownLocation = lastKnownLocation;
    }


    public static Camera.Size getCameraSize() {
        return mCameraSize;
    }

    public static void setCameraSize(Camera.Size size) {
        mCameraSize = size;
    }

    public static long getStartTimeNano() { return mStartTimeNano; }
    public static void setStartTimeNano(long startTimeNano) { mStartTimeNano = startTimeNano; }

    public static void setCollectionStatus(@NonNull final DataCollectionStatsView.Status status) {
        sStatus = status;
    }

    @Nullable
    public static DataCollectionStatsView.Status getCollectionStatus() {
        return sStatus;
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

    /**
     * Application state values.
     */
    public enum State {
        INIT,
        CALIBRATION,
        DATA,
        STABILIZATION,
        IDLE,
        RECONFIGURE,
    }
}
