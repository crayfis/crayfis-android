package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 1/29/15.
 */

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import edu.uci.crayfis.util.CFLog;

public class LayoutBlack extends Fragment{
    private static LayoutBlack mInstance =null;



    public LayoutBlack()
    {
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
        return root;
    }

    }
