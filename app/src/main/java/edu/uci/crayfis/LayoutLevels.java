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


import edu.uci.crayfis.widget.StatusView;

import edu.uci.crayfis.widget.LevelView;

public class LayoutLevels extends Fragment{

    // Widgets for giving feedback to the user.
    public static StatusView mStatusView;
    public static LevelView mLevelView;

    private static LayoutLevels mInstance =null;


    public LayoutLevels()
    {

    }

    public static LayoutLevels getInstance() {
        if (mInstance==null)
          mInstance = new LayoutLevels();


        return mInstance;
    }




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.levels, null);

        mStatusView = (StatusView) root.findViewById(R.id.status_view);
        mLevelView = (LevelView) root.findViewById(R.id.level_view);

        return root;
    }

    private static boolean shown_message=false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser)
        {
            if (!shown_message) {
             Toast.makeText(getActivity(), "This pane shows your current level.", Toast.LENGTH_LONG).show();
            shown_message = true;
            }
        }
        super.setUserVisibleHint(isVisibleToUser);
    }

}
