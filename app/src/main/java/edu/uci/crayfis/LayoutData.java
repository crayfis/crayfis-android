package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.particle.ParticleReco;
import edu.uci.crayfis.widget.DataView;
import edu.uci.crayfis.widget.LightMeter;
import edu.uci.crayfis.widget.MessageView;


public class LayoutData extends Fragment{

    // Widgets for giving feedback to the user.
    public static MessageView mMessageView;
    public static LightMeter mLightMeter;
    public static ProgressWheel mProgressWheel;
    public static DataView mDataView;


    private static LayoutData mInstance =null;

    private static L1Calibrator mL1Calibrator;
    private static ParticleReco mParticleReco;


    public LayoutData()
    {
        mL1Calibrator = L1Calibrator.getInstance();
        mParticleReco = ParticleReco.getInstance();
    }

    public static LayoutData getInstance() {
        if (mInstance==null)
          mInstance = new LayoutData();


        return mInstance;
    }


    public static void updateData() {

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

        mDataView = (DataView) root.findViewById(R.id.data_view);

        mProgressWheel = (ProgressWheel) root.findViewById(R.id.pw_spinner);
        mLightMeter = (LightMeter) root.findViewById(R.id.lightmeter);

        mProgressWheel.spin();

        mMessageView = (MessageView) root.findViewById(R.id.message_view);

        return root;
    }

    private static boolean shown_message=false;

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

}
