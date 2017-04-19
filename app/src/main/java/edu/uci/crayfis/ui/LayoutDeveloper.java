package edu.uci.crayfis.ui;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import edu.uci.crayfis.R;
import edu.uci.crayfis.widget.AppBuildView;


public class LayoutDeveloper extends Fragment{

    // Widgets for giving feedback to the user.
    public static TextView mTextView;

    public static AppBuildView mAppBuildView;

    private static LayoutDeveloper mInstance =null;

    public LayoutDeveloper()
    {
    }

    public static LayoutDeveloper getInstance() {
        if (mInstance==null)
          mInstance = new LayoutDeveloper();


        return mInstance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.devel, null);


        mTextView = (TextView)root.findViewById(R.id.devel_text);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        mAppBuildView = (AppBuildView) root.findViewById(R.id.app_build_view);


        return root;
    }

    private static boolean shown_message=false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser)
        {
            if (!shown_message) {
                Toast.makeText(getActivity(), R.string.toast_devel, Toast.LENGTH_LONG).show();
                shown_message = true;
            }
        }
        super.setUserVisibleHint(isVisibleToUser);
    }

}
