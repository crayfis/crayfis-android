package io.crayfis.android.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 10/5/2017.
 */

/**
 * Class to upload leftover files when Wifi is connected
 */
public class WifiReceiver extends BroadcastReceiver {

    private long WIFI_WAIT_TIME = 5000;

    @Override
    public void onReceive(final Context context, Intent intent) {

        CFLog.d("receiver: got action=" + intent.getAction());

        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if(info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI) {

            CFLog.d("Connected to Wifi");

            final Handler handler = new Handler(context.getMainLooper());

            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    CFApplication application = (CFApplication) context.getApplicationContext();
                    if (application.isNetworkAvailable()) {
                        UploadExposureService.uploadFileCache(context);
                    }
                }
            };

            // wait until Wifi is made the default network
            // FIXME: there has to be a better way of doing this
            handler.postDelayed(runnable, WIFI_WAIT_TIME);

        }
    }
}
