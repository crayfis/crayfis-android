package io.crayfis.android.ui.navdrawer.live_view;

/**
 * Created by danielwhiteson on 1/29/15.
 */

import android.os.Bundle;
import androidx.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import android.content.Context;
import java.util.ArrayList;

import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.ui.navdrawer.NavDrawerFragment;

public class LayoutLiveView extends NavDrawerFragment {

    static final Object event_lock = new Object();

    static final int max_events = 100;
    static final ArrayList<DataProtos.Event> events = new ArrayList<>(max_events);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        events.clear();
    }

    private boolean shown_message=false;

    private static final @StringRes int ABOUT_ID = R.string.toast_black;

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

    public static void addEvent(DataProtos.Event event)
    {
        synchronized(event_lock)
        {
            events.add(event);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.live_view, container, false);
    }


    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

}
