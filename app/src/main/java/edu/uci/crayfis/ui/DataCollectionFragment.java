package edu.uci.crayfis.ui;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.CFUtil;
import edu.uci.crayfis.DAQActivity;
import edu.uci.crayfis.DAQService;
import edu.uci.crayfis.R;
import edu.uci.crayfis.server.UploadExposureTask;
import edu.uci.crayfis.util.CFLog;
import edu.uci.crayfis.widget.DataCollectionStatsView;

/**
 * Fragment for showing current data collection status.
 */
public class DataCollectionFragment extends CFFragment {

    private TextView mStatus;
    private TextView mStatusMessage;
    private static String mIdleStatus;
    private DataCollectionStatsView mDataCollectionStats;
    private TextView mErrorMessage;
    private ProgressBar mProgressBar;
    private StateChangeReceiver mStateChangeReceiver;

    private final @StringRes int ABOUT_ID = R.string.toast_status;

    public static DataCollectionFragment getInstance() {
        return new DataCollectionFragment();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mStateChangeReceiver);
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rtn = inflater.inflate(R.layout.fragment_data_collection, container, false);
        mStatus = (TextView) rtn.findViewById(R.id.data_collection_status);
        mProgressBar = (ProgressBar) rtn.findViewById(R.id.progress_bar);
        mStatusMessage = (TextView) rtn.findViewById(R.id.data_collection_message);
        mDataCollectionStats = (DataCollectionStatsView) rtn.findViewById(R.id.data_collection_stats);
        mErrorMessage = (TextView) rtn.findViewById(R.id.data_collection_error);

        // There is a small window of opportunity where the receiver is not registered in time for
        // the transition to data collection.  The user may be stuck viewing a screen that says
        // it's calibrating.
        mStateChangeReceiver = new StateChangeReceiver();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mStateChangeReceiver,
                new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
        updateStatus();

        return rtn;
    }

    private void updateStatus() {
        final Activity activity = getActivity();
        if (mStatus == null || mStatusMessage == null || activity == null || activity.isFinishing()) {
            return;
        }

        final CFApplication.State appState = ((CFApplication) activity.getApplication()).getApplicationState();
        CFLog.d("AppState = " + appState);
        switch(appState) {
            case STABILIZATION:
                mStatus.setText(R.string.stabilization);
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                mDataCollectionStats.setVisibility(View.GONE);
                break;
            case PRECALIBRATION:
                mStatus.setText(getString(R.string.precalibration));
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                mDataCollectionStats.setVisibility(View.GONE);
                break;
            case CALIBRATION:
                mStatus.setText(R.string.calibration);
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                mDataCollectionStats.setVisibility(View.GONE);
                break;
            case DATA:
                mStatus.setText(R.string.taking_data);
                mProgressBar.setVisibility(View.INVISIBLE);
                mDataCollectionStats.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                break;
            case IDLE:
                mStatus.setText(getString(R.string.idle));
                mProgressBar.setVisibility(View.INVISIBLE);
                mDataCollectionStats.setVisibility(View.GONE);
                setStatusMessage(mIdleStatus);
                break;
            case INIT:
                mStatus.setText(getString(R.string.init));
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                mDataCollectionStats.setVisibility(View.GONE);
                break;
            default:
                mStatus.setText("Unknown State");
                mProgressBar.setVisibility(View.INVISIBLE);
                setStatusMessage("An unknown state has occurred.");
                mDataCollectionStats.setVisibility(View.GONE);
        }
    }

    private void setStatusMessage(@NonNull final String message) {
        if (mStatusMessage.getVisibility() != View.VISIBLE) {
            mStatusMessage.setVisibility(View.VISIBLE);
        }
        mStatusMessage.setText(message);
    }

    /**
     * Set the error message.
     *
     * @param resId Error message resource or {@code 0} to hide the error view.
     */
    private void setErrorMessage(final int resId) {
        if (resId <= 0) {
            if (mErrorMessage.getVisibility() != View.GONE) {
                mErrorMessage.setVisibility(View.GONE);
            }
        } else {
            if (mErrorMessage.getVisibility() != View.VISIBLE) {
                mErrorMessage.setVisibility(View.VISIBLE);
            }
            mErrorMessage.setText(resId);
        }
    }

    public void updateIdleStatus(String status) {
        mIdleStatus = status;
    }

    /**
     * Check if the location is valid.
     *
     * @param location {@link Location}
     * @return Whether the location is valid or not.
     */
    private boolean isLocationValid(@Nullable final Location location) {
        return (location != null
                && java.lang.Math.abs(location.getLongitude())>0.1
                && java.lang.Math.abs(location.getLatitude())>0.1);
    }

    /**
     * Set the error message.
     *
     * @param message The message or {@code null} to hide the error view.
     */
    private void setErrorMessage(@Nullable final String message) {
        if (message == null) {
            if (mErrorMessage.getVisibility() != View.GONE) {
                mErrorMessage.setVisibility(View.GONE);
            }
        } else {
            if (mErrorMessage.getVisibility() != View.VISIBLE) {
                mErrorMessage.setVisibility(View.VISIBLE);
            }
            mErrorMessage.setText(message);
        }
    }


    /**
     * Receiver for when the application state changes.
     */
    private final class StateChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            updateStatus();
        }
    }

    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

    @Override
    public void update() {
        final Activity activity = getActivity();
        if (!CFUtil.isActivityValid(activity)) {
            return;
        }

        final CFApplication application = (CFApplication) activity.getApplication();
        if (!isLocationValid(CFApplication.getLastKnownLocation())) {
            setErrorMessage(R.string.location_warning);
        } else if (!application.isNetworkAvailable()) {
            setErrorMessage(R.string.network_unavailable);
        } else if(CFApplication.badFlatEvents >= 5
                && CFConfig.getInstance().getCameraSelectMode() == CFApplication.MODE_FACE_DOWN) {
            setErrorMessage(R.string.sensor_error);
        } else if (!UploadExposureTask.sValidId.get()) {
            setErrorMessage(R.string.bad_user_code);
        } else if (!UploadExposureTask.sPermitUpload.get()) {
            setErrorMessage(R.string.server_overload);
        } else {
            setErrorMessage(0);
        }

        if (mDataCollectionStats.getVisibility() == View.VISIBLE) {
            DAQService.DAQBinder binder = DAQActivity.getBinder();
            if(binder != null) {
                final DataCollectionStatsView.Status status = binder.getDataCollectionStatus();
                if (status != null) {
                    mDataCollectionStats.setStatus(status);
                }
            }

        }
    }
}
