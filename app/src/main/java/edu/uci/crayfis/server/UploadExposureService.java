package edu.uci.crayfis.server;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.R;

/**
 * Created by jodi on 2015-01-25.
 */
public class UploadExposureService extends IntentService {

    private static final CFConfig sConfig = CFConfig.getInstance();
    private static CFApplication.AppBuild sAppBuild;
    private static ServerInfo sServerInfo;
    private static Boolean sDebugStream;

    private static boolean sPermitUpload = true;
    private static boolean sValidId = true;
    private static boolean sStartUploading;

    public UploadExposureService() {
        super("Exposure Uploader");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final CFApplication context = (CFApplication) getApplicationContext();
        if (sAppBuild == null) {
            sAppBuild = context.getBuildInformation();
        }
        if (sServerInfo == null) {
            sServerInfo = new ServerInfo(context);
        }
        if (sDebugStream == null) {
            sDebugStream = context.getResources().getBoolean(R.bool.debug_stream);
        }
        android.util.Log.d(":::", "Bonk.");
    }


    private boolean useWifiOnly() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPrefs.getBoolean("prefWifiOnly", true);
    }

    // Some utilities for determining the network state
    private NetworkInfo getNetworkInfo() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    // Check if there is *any* connectivity
    private boolean isConnected() {
        NetworkInfo info = getNetworkInfo();
        return (info != null && info.isConnected());
    }

    // Check if we're connected to WiFi
    private boolean isConnectedWifi() {
        NetworkInfo info = getNetworkInfo();
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }

    private boolean canUpload() {
        return sPermitUpload && ( (!useWifiOnly() && isConnected()) || isConnectedWifi());
    }

    /**
     * POJO for the server information.
     */
    private static final class ServerInfo {
        private final String uploadUrl;
        private final int connectTimeout = 2 * 1000; // ms
        private final int readTimeout = 5 * 1000; // ms

        private ServerInfo(@NonNull final Context context) {
            final String serverAddress = context.getString(R.string.server_address);
            final String serverPort = context.getString(R.string.server_port);
            final String uploadUri = context.getString(R.string.upload_uri);
            final boolean forceHttps = context.getResources().getBoolean(R.bool.force_https);

            uploadUrl = String.format("%s://%s:%s%s",
                    forceHttps ? "https" : "http",
                    serverAddress,
                    serverPort,
                    uploadUri);
        }
    }
}
