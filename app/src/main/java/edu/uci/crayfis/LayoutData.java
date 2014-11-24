package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import edu.uci.crayfis.particle.ParticleReco;
import edu.uci.crayfis.widget.AppBuildView;
import edu.uci.crayfis.widget.MessageView;
import edu.uci.crayfis.widget.StatusView;

public class LayoutData extends Fragment{

    // Widgets for giving feedback to the user.
    public static StatusView mStatusView;
    public static MessageView mMessageView;

    public static ProgressWheel mProgressWheel;

    private static LayoutData mInstance =null;

    public LayoutData()
    {
    }

    public static LayoutData getInstance() {
        if (mInstance==null)
          mInstance = new LayoutData();


        return mInstance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.data, null);


        mProgressWheel = (ProgressWheel) root.findViewById(R.id.pw_spinner);
        mProgressWheel.spin();
        mStatusView = (StatusView) root.findViewById(R.id.status_view);
        mMessageView = (MessageView) root.findViewById(R.id.message_view);

        return root;
    }

}
