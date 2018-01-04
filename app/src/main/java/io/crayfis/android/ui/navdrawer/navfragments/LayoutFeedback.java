package io.crayfis.android.ui.navdrawer.navfragments;

/**
 * Created by danielwhiteson on 1/5/15.
 */

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import io.crayfis.android.R;
import io.crayfis.android.util.CFLog;


public class LayoutFeedback extends CFFragment {


    private static LayoutFeedback mInstance =null;

    public static LayoutFeedback getInstance() {
        if (mInstance==null)
            mInstance= new LayoutFeedback();

        return mInstance;
    }
    private static boolean shown_message=false;

    private final @StringRes int ABOUT_ID = R.string.toast_feedback;


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

        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.feedback, null);

        //Removes the title bar in the application
        //requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Creation of the Webview found in the XML Layout file
        browserView = (WebView)root.findViewById(R.id.webkit_feedback);
        browserView.setWebViewClient(new WebViewClient());
        browserView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(final View v, final int keyCode, final KeyEvent event) {
                if (browserView.canGoBack()) {
                    browserView.goBack();
                    return true;
                }
                return false;
            }
        });

        mProgressBar = (ProgressBar)root.findViewById(R.id.webprogress_feedback);
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
        final String leaderURL = "https://crayfis.uservoice.com/forums/272573-general";

        CFLog.d("CRAYFIS loading" + leaderURL);
        browserView.loadUrl(leaderURL);

        return root;
    }

    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }
}
