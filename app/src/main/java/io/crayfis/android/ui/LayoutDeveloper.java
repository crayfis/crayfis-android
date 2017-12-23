package io.crayfis.android.ui;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.os.Bundle;
import android.support.annotation.StringRes;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import io.crayfis.android.CFApplication;
import io.crayfis.android.DAQActivity;
import io.crayfis.android.DAQService;
import io.crayfis.android.R;
import io.crayfis.android.widget.AppBuildView;


public class LayoutDeveloper extends CFFragment {

    // Widgets for giving feedback to the user.
    public TextView mTextView;

    public AppBuildView mAppBuildView;

    private static LayoutDeveloper mInstance =null;

    private final @StringRes int ABOUT_ID = R.string.toast_devel;

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

        //startUiUpdate(new UiUpdateRunnable());


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


    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

    @Override
    public void update() {
        final DAQActivity activity = (DAQActivity)getActivity();
        final CFApplication application = (CFApplication) (activity.getApplication());
        mAppBuildView.setAppBuild(application.getBuildInformation());
        final DAQService.DAQBinder binder = activity.getBinder();
        if(binder != null) {
            mTextView.setText(binder.getDevText());
        }
    }

}