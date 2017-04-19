package edu.uci.crayfis.ui;

/**
 * Created by danielwhiteson on 1/29/15.
 */

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.hardware.Camera;

import android.content.Context;
import java.util.ArrayList;

import edu.uci.crayfis.CFUtil;
import edu.uci.crayfis.R;
import edu.uci.crayfis.trigger.L2Task;
import edu.uci.crayfis.widget.SplashView;

public class LayoutBlack extends CFFragment{
    private static LayoutBlack mInstance =null;

    public SplashView mSplashView;

    public final Object event_lock = new Object();

    public Camera.Size previewSize;

    public LayoutBlack()
    {
    }

    private boolean shown_message=false;

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

        startUiUpdate(new UiUpdateRunnable());

        return root;
    }

    /*
     * Runnable to update the UI
     */
    private final class UiUpdateRunnable implements Runnable {

        @Override
        public void run() {
            final Activity activity = getActivity();
            if (!CFUtil.isActivityValid(activity)) {
                return;
            }

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
            boolean show_splashes = sharedPrefs.getBoolean("prefSplashView", true);
            if (show_splashes) {
                try {
                    L2Task.RecoEvent ev = null; //l2thread.getDisplayPixels().poll(10, TimeUnit.MILLISECONDS);
                    if (ev != null) {
                        //CFLog.d(" L2thread poll returns an event with " + ev.pixels.size() + " pixels time=" + ev.time + " pv =" + previewSize);
                        addEvent(ev);
                    } else {
                        // CFLog.d(" L2thread poll returns null ");
                    }

                } catch (Exception e) {
                    // just don't do it
                }
            }
        }
    }

}
