package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;



public class LayoutDeveloper extends Fragment{

    // Widgets for giving feedback to the user.
    public static TextView mTextView;


    private static LayoutDeveloper mInstance =null;

    public LayoutDeveloper()
    {
    }

    public static Fragment getInstance() {
        if (mInstance==null)
          mInstance = new LayoutDeveloper();


        return mInstance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.devel, null);


        mTextView = (TextView)root.findViewById(R.id.devel_text);


        return root;
    }

}
