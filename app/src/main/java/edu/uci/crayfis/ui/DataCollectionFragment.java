package edu.uci.crayfis.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFUtil;
import edu.uci.crayfis.DAQActivity;
import edu.uci.crayfis.R;
import edu.uci.crayfis.server.UploadExposureTask;
import edu.uci.crayfis.widget.DataCollectionStatsView;

/**
 * Fragment for showing current data collection status.
 */
public class DataCollectionFragment extends CFFragment {

    private TextView mStatus;
    private TextView mStatusMessage;
    private DataCollectionStatsView mDataCollectionStats;
    private TextView mErrorMessage;
    private ProgressBar mProgressBar;
    private StateChangeReceiver mStateChangeReceiver;

    public static DataCollectionFragment getInstance() {
        return new DataCollectionFragment();
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        mStateChangeReceiver = new StateChangeReceiver();
        LocalBroadcastManager.getInstance(activity).registerReceiver(mStateChangeReceiver,
                new IntentFilter(CFApplication.ACTION_STATE_CHANGE));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        updateStatus();
        startUiUpdate(new UiUpdateRunnable());
        return rtn;
    }

    private void updateStatus() {
        final Activity activity = getActivity();
        if (mStatus == null || mStatusMessage == null || activity == null || activity.isFinishing()) {
            return;
        }

        final CFApplication.State appState = ((CFApplication) activity.getApplication()).getApplicationState();
        switch(appState) {
            case STABILIZATION:
            case CALIBRATION:
                mStatus.setText("Calibrating...");
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                mDataCollectionStats.setVisibility(View.GONE);
                break;
            case DATA:
                mStatus.setText("Collecting Data");
                mProgressBar.setVisibility(View.INVISIBLE);
                mDataCollectionStats.setVisibility(View.VISIBLE);
                setStatusMessage(null);
                break;
            case IDLE:
                mStatus.setText("Idle");
                mProgressBar.setVisibility(View.INVISIBLE);
                mDataCollectionStats.setVisibility(View.GONE);
                //TODO: Explain why the app is idle.
                setStatusMessage("TODO: Explain why the app is idle.");
                break;
            case INIT:
                mStatus.setText("Initializing...");
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
     * Runnable to update the UI.
     *
     * For error message display, the order of importance is
     * <ul>
     *     <li>Frames are being dropped.</li>
     *     <li>No location available.</li>
     *     <li>No network available.</li>
     *     <li>Bad user code.</li>
     *     <li>Server is overloaded.</li>
     * </ul>
     */
    private final class UiUpdateRunnable implements Runnable {

        @Override
        public void run() {
            // TODO Add no location.

            final Activity activity = getActivity();
            if (!CFUtil.isActivityValid(activity)) {
                return;
            }

            final CFApplication application = (CFApplication) activity.getApplication();
            if (DAQActivity.L2busy > 0) {
                final String ignoredFrames = getResources().getQuantityString(R.plurals.total_frames, DAQActivity.L2busy, DAQActivity.L2busy);
                setErrorMessage(getResources().getString(R.string.ignored) + " " + ignoredFrames);
            } else if (!isLocationValid(CFApplication.getLastKnownLocation())) {
                setErrorMessage(R.string.location_warning);
            } else if (!application.isNetworkAvailable()) {
                setErrorMessage(R.string.network_unavailable);
            } else if (!UploadExposureTask.sValidId.get()) {
                    setErrorMessage(R.string.bad_user_code);
            } else if (!UploadExposureTask.sPermitUpload.get()) {
                setErrorMessage(R.string.server_overload);
            } else {
                setErrorMessage(0);
            }

            if (mDataCollectionStats.getVisibility() == View.VISIBLE) {
                final DataCollectionStatsView.Status status = CFApplication.getCollectionStatus();
                if (status != null) {
                    mDataCollectionStats.setStatus(status);
                }
            }
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
}
