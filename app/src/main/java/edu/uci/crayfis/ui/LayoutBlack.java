package edu.uci.crayfis.ui;

/**
 * Created by danielwhiteson on 1/29/15.
 */

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.hardware.Camera;

import android.content.Context;
import java.util.ArrayList;

import edu.uci.crayfis.R;
import edu.uci.crayfis.trigger.L2Task;
import edu.uci.crayfis.widget.SplashView;

public class LayoutBlack extends Fragment{
    private static LayoutBlack mInstance =null;

    public static SplashView mSplashView;

    public final static Object event_lock = new Object();

    public static Camera.Size previewSize;

    public LayoutBlack()
    {
    }

    private static boolean shown_message=false;

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
        else {  }
        super.setUserVisibleHint(isVisibleToUser);
    }

    private static final int max_events = 100;
    public ArrayList<L2Task.RecoEvent> events = new ArrayList<L2Task.RecoEvent>(max_events);

    public void addEvent(L2Task.RecoEvent p)
    {
        synchronized(event_lock)
        {
            events.add(p);
        }
        //CFLog.d(" Layoutblack "+this+" add event with "+p.pixels.size()+ " from time "+p.time+ " #events="+events.size());

    }

    public static LayoutBlack getInstance() {
        if (mInstance==null)
            mInstance = new LayoutBlack();

        return mInstance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState)
    {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.black, null);


        mSplashView = (SplashView) root.findViewById(R.id.splash_view);

        //CFLog.d(" LayoutBlack splashview = "+mSplashView+" with #events="+events.size());



        return root;
    }

    }
