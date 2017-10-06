package io.crayfis.android.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 10/5/2017.
 */

/**
 * Class to upload leftover files when Wifi is connected
 */
public class WifiReceiver extends BroadcastReceiver {

    private static final ArrayDeque<File> FILE_DEQUE = new ArrayDeque<>();

    @Override
    public void onReceive(final Context context, Intent intent) {

        CFLog.d("receiver: got action=" + intent.getAction());
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        if(info == null) return;
        if(info.isConnected()) {

            // make sure we only try to upload files once
            synchronized (FILE_DEQUE) {
                if(FILE_DEQUE.isEmpty()) {
                    FILE_DEQUE.addAll(Arrays.asList(context.getFilesDir().listFiles()));

                    // upload files slow enough to not overwhelm the server
                    final Timer uploadTimer = new Timer("UploadTimer");
                    uploadTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            synchronized (FILE_DEQUE) {
                                // quit TimerTask if no more files
                                if(FILE_DEQUE.isEmpty()) {
                                    uploadTimer.cancel();
                                    return;
                                }
                                File f = FILE_DEQUE.poll();
                                UploadExposureService.submitFile(context, f);
                            }
                        }
                    }, 5000L, 200L);
                }
            }
        } else {
            // quit if Wifi is disconnected
            synchronized (FILE_DEQUE) {
                FILE_DEQUE.clear();
            }
        }
    }
}
