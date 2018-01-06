package io.crayfis.android.ui.navdrawer.live_view;

/**
 * Created by danielwhiteson on 1/29/15.
 */

import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.hardware.Camera;

import android.content.Context;
import java.util.ArrayList;

import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.ui.navdrawer.NavDrawerFragment;

public class LayoutLiveView extends NavDrawerFragment {
    private static LayoutLiveView mInstance =null;

    public SplashView mSplashView;

    public final Object event_lock = new Object();

    public Camera.Size previewSize;

    public LayoutLiveView()
    {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        events = new ArrayList<>(max_events);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        events = null;
    }

    private boolean shown_message=false;

    private final @StringRes int ABOUT_ID = R.string.toast_black;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {


            super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser)
        {
           if (!shown_message)
           {
               Context cx = getActivity();
               if (cx != null) {
                   Toast.makeText(cx, getResources().getString(R.string.toast_black), Toast.LENGTH_LONG).show();
                   shown_message = true;
               }
           }
        }

        super.setUserVisibleHint(isVisibleToUser);
    }

    public ArrayList<DataProtos.Event> events;
    private static final int max_events = 100;

    public void addEvent(DataProtos.Event event)
    {
        synchronized(event_lock)
        {
            events.add(event);
        }
        //CFLog.d(" Layoutblack "+this+" add event with "+p.pixels.size()+ " from time "+p.time+ " #events="+events.size());

    }

    public static LayoutLiveView getInstance() {
        if (mInstance==null)
            mInstance = new LayoutLiveView();

        return mInstance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState)
    {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.black, null);


        mSplashView = (SplashView) root.findViewById(R.id.splash_view);

        //CFLog.d(" LayoutLiveView splashview = "+mSplashView+" with #events="+events.size());

        return root;
    }


    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

}
