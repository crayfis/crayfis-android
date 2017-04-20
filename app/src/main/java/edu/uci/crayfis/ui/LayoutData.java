package edu.uci.crayfis.ui;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.CFUtil;
import edu.uci.crayfis.R;
import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.widget.DataCollectionStatsView;
import edu.uci.crayfis.widget.LightMeter;
import edu.uci.crayfis.widget.MessageView;
import edu.uci.crayfis.widget.ProgressWheel;


public class LayoutData extends CFFragment {

    // Widgets for giving feedback to the user.
    public MessageView mMessageView;
    public LightMeter mLightMeter;
    public ProgressWheel mProgressWheel;
    public DataCollectionStatsView mDataView;


    private static LayoutData mInstance =null;

    private L1Calibrator mL1Calibrator;

    private final @StringRes int ABOUT_ID = R.string.help_data;


    public LayoutData()
    {
        mL1Calibrator = L1Calibrator.getInstance();
    }

    public static LayoutData getInstance() {
        if (mInstance==null)
          mInstance = new LayoutData();


        return mInstance;
    }


    public void updateData() {

        if (mL1Calibrator !=null) {
            Integer[] values = new Integer[mL1Calibrator.getMaxPixels().size()];
            values = mL1Calibrator.getMaxPixels().toArray(values);

            // want very responsive data, so use latest
            if (values.length > 0)
                mLightMeter.setLevel(values[values.length - 1]);
            else
                mLightMeter.setLevel(0);

        }


            mLightMeter.setGood(mLightMeter.getLevel() < 50);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.data, container, false);

        mDataView = (DataCollectionStatsView) root.findViewById(R.id.data_collection_stats);

        mProgressWheel = (ProgressWheel) root.findViewById(R.id.pw_spinner);
        mLightMeter = (LightMeter) root.findViewById(R.id.lightmeter);

        mProgressWheel.spin();

        mMessageView = (MessageView) root.findViewById(R.id.message_view);

        return root;
    }

    private boolean shown_message=false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser)
        {
            if (!shown_message) {
             Toast.makeText(getActivity(), R.string.toast_data, Toast.LENGTH_LONG).show();
            shown_message = true;
            }
        }
        super.setUserVisibleHint(isVisibleToUser);
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

        //l2thread.setmSaveImages(sharedPrefs.getBoolean("prefEnableGallery", false));

        // Originally, the updating of the LevelView was done here.  This seems like a good place to also
        // make sure that UserStatusView gets updated with any new counts.
        final View userStatus = activity.findViewById(R.id.user_status);
        if (userStatus != null) {
            userStatus.postInvalidate();
        }

        try {

            if (mLightMeter != null) {
                updateData();
            }

            if (application.getApplicationState() == CFApplication.State.IDLE)
            {
                if (mProgressWheel != null) {
                    mProgressWheel.setText("");

                    mProgressWheel.setTextColor(Color.WHITE);
                    mProgressWheel.setBarColor(Color.LTGRAY);

                    int progress = 0; //(int) (360 * batteryPct);
                    mProgressWheel.setProgress(progress);
                    mProgressWheel.stopGrowing();
                    mProgressWheel.doNotShowBackground();
                }
            }


            if (application.getApplicationState() == CFApplication.State.STABILIZATION)
            {
                if (mProgressWheel != null) {
                    mProgressWheel.setText(getResources().getString(R.string.stabilization));

                    mProgressWheel.setTextColor(Color.RED);
                    mProgressWheel.setBarColor(Color.RED);

                    mProgressWheel.stopGrowing();
                    mProgressWheel.spin();
                    mProgressWheel.doNotShowBackground();
                }
            }


            if (application.getApplicationState() == CFApplication.State.CALIBRATION)
            {
                if (mProgressWheel != null) {

                    mProgressWheel.setText(getResources().getString(R.string.calibration));

                    mProgressWheel.setTextColor(Color.RED);
                    mProgressWheel.setBarColor(Color.RED);

                    int needev = CFConfig.getInstance().getCalibrationSampleFrames();
                    float frac = L1Calibrator.getMaxPixels().size() / ((float) 1.0 * needev);
                    int progress = (int) (360 * frac);
                    mProgressWheel.setProgress(progress);
                    mProgressWheel.stopGrowing();
                    mProgressWheel.showBackground();


                }
            }
            if (application.getApplicationState() == CFApplication.State.DATA)
            {
                if (mProgressWheel != null) {
                    mProgressWheel.setText(getResources().getString(R.string.taking_data));
                    mProgressWheel.setTextColor(0xFF00AA00);
                    mProgressWheel.setBarColor(0xFF00AA00);

                    // solid circle
                    mProgressWheel.setProgress(360);
                    mProgressWheel.showBackground();
                    mProgressWheel.grow();

                }
            }
        } catch (OutOfMemoryError e) { // don't crash of OOM, just don't update UI

        }
    }

}
