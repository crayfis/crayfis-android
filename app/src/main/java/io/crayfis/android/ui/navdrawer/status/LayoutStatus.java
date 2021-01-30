package io.crayfis.android.ui.navdrawer.status;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.ui.navdrawer.NavDrawerFragment;
import io.crayfis.android.util.CFUtil;
import io.crayfis.android.main.DAQActivity;
import io.crayfis.android.main.DAQService;
import io.crayfis.android.R;
import io.crayfis.android.server.UploadExposureService;

/**
 * Fragment for showing current data collection status.
 */
public class LayoutStatus extends NavDrawerFragment {

    private TextView mStatus;
    private TextView mStatusMessage;
    private static String mIdleStatus;
    private DataCollectionStatsView mDataCollectionStats;
    private TextView mErrorMessage;
    private ProgressBar mProgressBar;
    private StateChangeReceiver mStateChangeReceiver;

    private static final @StringRes int ABOUT_ID = R.string.toast_status;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mStateChangeReceiver);
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rtn = inflater.inflate(R.layout.status, container, false);
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
        switch(appState) {
            case SURVEY:
                mStatus.setText(R.string.survey);
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                mDataCollectionStats.setVisibility(View.GONE);
                break;
            case PRECALIBRATION:
                mStatus.setText(R.string.precalibration);
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.VISIBLE);
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
                mStatus.setText(R.string.idle);
                mProgressBar.setVisibility(View.INVISIBLE);
                mDataCollectionStats.setVisibility(View.GONE);
                setStatusMessage("");
                break;
            case FINISHED:
                mStatus.setText(R.string.idle);
                mProgressBar.setVisibility(View.INVISIBLE);
                mDataCollectionStats.setVisibility(View.GONE);
                setStatusMessage(getString(R.string.idle_finished));
                break;
            case INIT:
                mStatus.setText(R.string.init);
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

    public static void updateIdleStatus(String status) {
        mIdleStatus = status;
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
        final CFConfig config = CFConfig.getInstance();
        switch (application.getApplicationState()) {
            case IDLE:
                setStatusMessage(mIdleStatus);
                break;
            case PRECALIBRATION:
            case CALIBRATION:
                final int count, total;
                String statusMessage = "";
                if(application.getApplicationState() == CFApplication.State.PRECALIBRATION) {
                    ExposureBlockManager xbManager = ExposureBlockManager.getInstance();
                    if(xbManager.getCurrentExposureBlock().daq_state == CFApplication.State.PRECALIBRATION) {
                        count = xbManager.getCurrentExposureBlock().count.intValue();
                        PreCalibrator precal = (PreCalibrator) xbManager.getCurrentExposureBlock()
                                .TRIGGER_CHAIN.getProcessor(PreCalibrator.class);
                        if (precal == null) return;
                        total = precal.getCurrentConfig().getInt(TriggerProcessor.Config.KEY_MAXFRAMES);
                        statusMessage += String.format(getString(R.string.status_step),
                                precal.getStepNumber() + 1, precal.getTotalSteps()) + "\n";
                    } else {
                        // we must be connecting to the server
                        setStatusMessage(getString(R.string.status_server));
                        break;
                    }
                } else {
                    ExposureBlock xb = ExposureBlockManager.getInstance().getCurrentExposureBlock();
                    count = xb != null ? xb.count.intValue() : 0;
                    total = config.getCalibrationSampleFrames();
                }
                int pct = 100*count/total;
                int sLeft = (int)((total-count)/ DAQManager.getInstance().getFPS());
                statusMessage += String.format(getString(R.string.status_pct), pct, sLeft/60, sLeft%60);
                setStatusMessage(statusMessage);

        }

        if (application.getApplicationState() == CFApplication.State.FINISHED) {
            setErrorMessage(0);
        } else if (!DAQManager.getInstance().isUpdatingLocation()) {
            setErrorMessage(R.string.location_warning);
        } else if (!application.isNetworkAvailable()) {
            setErrorMessage(R.string.network_unavailable);
        } else if (!UploadExposureService.sValidId.get()) {
            setErrorMessage(R.string.bad_user_code);
        } else if (!UploadExposureService.sPermitUpload.get()) {
            setErrorMessage(R.string.server_overload);
        } else {
            setErrorMessage(0);
        }

        try {
            DAQService.DAQBinder binder = ((DAQActivity)getActivity()).getBinder();
            if (mDataCollectionStats.getVisibility() == View.VISIBLE) {
                mDataCollectionStats.setStatus(new DataCollectionStatsView.Status.Builder()
                        .setTotalFrames(binder.getTotalFrames())
                        .setTotalEvents(binder.getTotalEvents())
                        .setTotalPixels(binder.getTotalPixelsScanned())
                        .build());
            }
        } catch (Exception e) {
            // don't crash
        }
    }
}
