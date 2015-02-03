package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 1/5/15.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.ConsoleMessage;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;



import edu.uci.crayfis.util.CFLog;


public class LayoutLogin extends Fragment {


    private static LayoutLogin mInstance =null;

    public static LayoutLogin getInstance() {
        if (mInstance==null)
            mInstance= new LayoutLogin();

        return mInstance;
    }
    private static boolean shown_message=false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {


            if (!shown_message)
            {

                Toast.makeText(getActivity(), "Create an account or login to see your active devices.",
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

        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.login, null);

        //Removes the title bar in the application
        //requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Creation of the Webview found in the XML Layout file
        browserView = (WebView)root.findViewById(R.id.webkit_login);
        mProgressBar = (ProgressBar)root.findViewById(R.id.webprogress_login);
        mProgressBar.setVisibility(View.VISIBLE);

        //Enable Javascripts
        browserView.getSettings().setJavaScriptEnabled(true);

        browserView.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cmsg)
            {
                // check secret prefix
                if (cmsg.message().startsWith("MAGIC"))
                {
                    String msg = cmsg.message().substring(5); // strip off prefix

                    CFLog.d(" CRAYFIS got html data = "+msg.length());

                    /// get the user code
                    String code = "";
                    try
                    {
                        String tag = "your user code:";
                        int index = msg.indexOf(tag);
                        int code_start = index + tag.length() + 4; // +1 for space +3 for <b> tag
                        int code_length = 6;
                        if (code_start>=0)
                          code = msg.substring(code_start, code_start + code_length);
                        CFLog.d(" CRAYFIS got user code = " + code);
                    } catch (Exception e) {}

                    /// get the account name
                    String username = "";
                    try
                    {
                        String tag = "Hello,";
                        int index = msg.indexOf(tag);
                        int code_start = index + tag.length() + 9; // +1 for space +8 for <strong> tag
                        int code_end = msg.indexOf("</strong>",code_start)-1;
                        if (code_end>code_start)
                          username = msg.substring(code_start, code_end);
                        CFLog.d(" CRAYFIS got user name = " + username);
                    } catch (Exception e) {}

                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

                    if (code.length()==6)
                    {
                        sharedPrefs.edit().putString("prefUserID",code).apply();
                        Toast.makeText(getActivity(), "This device has been connected to account "+username+" with user code "+code,
                                Toast.LENGTH_SHORT).show();
                    }

                    return true;
                }

                return false;
            }
            public void onProgressChanged(WebView view, int progress) {
                mProgressBar.setProgress(progress);
                if (progress==100){ mProgressBar.setVisibility(View.GONE);}

            }
        });

        browserView.setWebViewClient( new WebViewClient() {
                                          public void onPageFinished(WebView view, String url)
                                          {
                                              CFLog.d("CRAYFIS has now loaded" + url);
                                              if (url.contains("monitor/dashboard")) {
                                                  view.loadUrl("javascript:console.log('MAGIC'+document.getElementsByTagName('html')[0].innerHTML);");
                                              }
                                          }
                                      });

        //Removes both vertical and horizontal scroll bars
        browserView.setVerticalScrollBarEnabled(false);
        browserView.setHorizontalScrollBarEnabled(false);

        //The website which is wrapped to the webview
        final String leaderURL = "http://"+server_address+"/accounts/login/";

        CFLog.d("CRAYFIS loading" + leaderURL);
        browserView.loadUrl(leaderURL);

        return root;
    }
}
