package edu.uci.crayfis.server;

import android.app.IntentService;
import android.content.Intent;

/**
 * Created by jodi on 2015-01-25.
 */
public class UploadExposureService extends IntentService {

    public UploadExposureService() {
        super("Exposure Uploader");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        android.util.Log.d(":::", "Bonk.");
    }
}
