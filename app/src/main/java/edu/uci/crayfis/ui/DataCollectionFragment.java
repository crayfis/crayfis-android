package edu.uci.crayfis.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.R;

/**
 * Created by jodi on 2015-06-11.
 */
public class DataCollectionFragment extends Fragment {

    private TextView mStatus;
    private TextView mStatusMessage;
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
            case STABILIZATION:
            case CALIBRATION:
                mStatus.setText("Calibrating...");
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                break;
            case DATA:
                mStatus.setText("Collecting Data");
                mProgressBar.setVisibility(View.INVISIBLE);
                mStatusMessage.setVisibility(View.VISIBLE);
                //TODO: Show the stats
                mStatusMessage.setText("TODO: Show the stats.");
                break;
            case IDLE:
                mStatus.setText("Idle");
                mProgressBar.setVisibility(View.INVISIBLE);
                mStatusMessage.setVisibility(View.VISIBLE);
                //TODO: Explain why the app is idle.
                mStatusMessage.setText("TODO: Explain why the app is idle.");
                break;
            case INIT:
                mStatus.setText("Initializing...");
                mProgressBar.setVisibility(View.VISIBLE);
                mStatusMessage.setVisibility(View.GONE);
                break;
            default:
                mStatus.setText("Unknown State");
                mProgressBar.setVisibility(View.INVISIBLE);
                mStatusMessage.setVisibility(View.VISIBLE);
                mStatusMessage.setText("An unknown state has occurred.");
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
