package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 1/5/15.
 */

import android.webkit.WebViewClient;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.webkit.WebChromeClient;
import android.view.View;
import android.content.Context;


import android.widget.Toast;


public class LayoutLeader extends Fragment {


    private static LayoutLeader mInstance =null;

    public static LayoutLeader getInstance() {
        if (mInstance==null)
            mInstance= new LayoutLeader();

        return mInstance;
    }
    private static boolean shown_message=false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {


            if (!shown_message)
            {

                Toast.makeText(getActivity(), R.string.toast_leader,
                        Toast.LENGTH_SHORT).show();
                shown_message=true;
            }


        }
        else {  }
        super.setUserVisibleHint(isVisibleToUser);
    }


    private String server_address;
    private String server_port;
    private Context context;


    WebView browserView;
    ProgressBar mProgressBar;
    /** Called when the activity is first created. */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);

         context = getActivity();
        server_address = context.getString(R.string.server_address);
        server_port = context.getString(R.string.server_port);

        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.leader, null);

        //Removes the title bar in the application
        //requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Creation of the Webview found in the XML Layout file
        browserView = (WebView)root.findViewById(R.id.webkit);
        browserView.setWebViewClient(new WebViewClient());
        mProgressBar = (ProgressBar)root.findViewById(R.id.webprogress);
        mProgressBar.setVisibility(View.VISIBLE);

        //Enable Javascripts
        browserView.getSettings().setJavaScriptEnabled(true);

        browserView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                mProgressBar.setProgress(progress);
                if (progress==100){ mProgressBar.setVisibility(View.GONE);}

            }
        });

        //Removes both vertical and horizontal scroll bars
        browserView.setVerticalScrollBarEnabled(false);
        browserView.setHorizontalScrollBarEnabled(false);

        //The website which is wrapped to the webview
        final String leaderURL = "http://"+server_address+"/embed/android";

        browserView.loadUrl(leaderURL);

        return root;
    }
}
