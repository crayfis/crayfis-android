package io.crayfis.android.server;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.util.CFLog;

/**
 * An implementation of AsyncTask that uploads a chunk from the local cache.
 *
 * Upon a successful upload, the chunk will be removed from the cache.  This will also
 * parse any response from the server and update the local configuration accordingly through
 * {@link CFConfig#updateFromServer(ServerCommand)}.
 */
class UploadExposureTask extends AsyncTask<Object, Object, Boolean> {

    private static final String INVALID_FILE = "invalid_file";
    private static final String MALFORMED_FILE = "malformed";

    private final CFApplication mApplication;
    private final UploadExposureService.ServerInfo mServerInfo;
    private final File mFile;
    private String mRunId;

    /**
     * Create a new instance.
     *
     * @param application {@link CFApplication}
     * @param serverInfo The {@link io.crayfis.android.server.UploadExposureService.ServerInfo} to use for uploading.
     * @param file The File to upload.
     */
    UploadExposureTask(@NonNull final CFApplication application,
                              @NonNull final UploadExposureService.ServerInfo serverInfo,
                              @NonNull final File file) {
        mApplication = application;
        mServerInfo = serverInfo;
        mFile = file;
    }

    @Override
    protected Boolean doInBackground(final Object... objects) {
        if (! canUpload()) {
            CFLog.w("Attempted to upload a block to the server but can't upload right now.");
            return Boolean.FALSE;
        }

        mRunId = getRunIdFromFile();
        if (mRunId.equals(MALFORMED_FILE) || mRunId.equals(INVALID_FILE)) {
            final String filename = mFile.getName();
            if (mRunId.equals(MALFORMED_FILE)) {
                CFLog.e(filename + " is malformed");
            }
            if (mRunId.equals(INVALID_FILE)) {
                CFLog.e(filename + " is invalid.");
            }
            return Boolean.FALSE;
        }

        try {
            final DataProtos.DataChunk chunk = DataProtos.DataChunk.
                    parseFrom(new FileInputStream(mFile));
            final ByteString rawData = chunk.toByteString();

            CFLog.i("Uploading a " + rawData.size() + "b from " + mFile.getName());

            if (uploadData(rawData)) {
                CFLog.d("Uploading " + mFile.getName() + " complete.");
                if (!mFile.delete()) {
                    CFLog.e("Could not delete file " + mFile.getName());
                }
            }
        } catch (IOException ex) {
            CFLog.e("Unable to upload file " + mFile.getName(), ex);
        } catch (OutOfMemoryError ex) {
            // FIXME Why was this even running out of memory?
            CFLog.e("Oh noes! An OutofMemory occured.", ex);
        }

        return Boolean.TRUE;
    }

    @NonNull
    private String getRunIdFromFile() {
        String filename = mFile.getName();
        if (! filename.endsWith(".bin")) {
            return INVALID_FILE;
        }

        String[] pieces = filename.split("_");
        if (pieces.length < 2) {
            return MALFORMED_FILE;
        }

        return pieces[0];
    }

    @NonNull
    private Boolean uploadData(@NonNull final ByteString rawData) throws IOException {
        URL u = new URL(mServerInfo.uploadUrl);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-type", "application/octet-stream");
        c.setRequestProperty("Content-length", String.format("%d", rawData.size()));
        c.setRequestProperty("Device-id", mServerInfo.deviceId);
        c.setRequestProperty("Run-id", mRunId);
        c.setRequestProperty("Crayfis-version", "a " + mServerInfo.buildVersion);
        c.setRequestProperty("Crayfis-version-code", Integer.toString(mServerInfo.versionCode));

        SharedPreferences sharedprefs = PreferenceManager.getDefaultSharedPreferences(mApplication);
        String app_code = sharedprefs.getString("prefUserID", "");
        if (app_code != null && ! app_code.isEmpty()) {
            c.setRequestProperty("App-code", app_code);
        }

        // FIXME This doesn't need to be called all the time, it's determined at compile time.
        if (mApplication.getResources().getBoolean(R.bool.debug_stream)) {
            c.setRequestProperty("Debug-stream", "yes");
        }

        c.setUseCaches(false);
        c.setAllowUserInteraction(false);
        c.setDoOutput(true);
        c.setConnectTimeout(mServerInfo.connectTimeout);
        c.setReadTimeout(mServerInfo.readTimeout);

        OutputStream os = c.getOutputStream();

        // try writing to the output stream
        rawData.writeTo(os);

        CFLog.i("Connecting to upload server at: " + mServerInfo.uploadUrl);
        c.connect();

        final int serverResponseCode = c.getResponseCode();

        if (serverResponseCode == 403 || serverResponseCode == 401) {
            // server rejected us! so we are not allowed to upload.
            // oh well! we can still take data at least.

            UploadExposureService.sPermitUpload.set(false);

            if (serverResponseCode == 401) {
                // server rejected us because our app code is invalid.
                SharedPreferences.Editor editor = sharedprefs.edit();
                editor.putBoolean("badID", true);
                editor.apply();
                CFLog.w("Setting bad ID flag!");
                UploadExposureService.sValidId.set(false);
            }

            return Boolean.FALSE;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }

        final ServerCommand serverCommand = new Gson().fromJson(sb.toString(), ServerCommand.class);
        CFConfig.getInstance().updateFromServer(serverCommand);
        mApplication.savePreferences();

        CFLog.i("Connected! Status = " + serverResponseCode);
        CFLog.d("Received json response:\n" + sb.toString());

        // and now disconnect
        c.disconnect();

        if (serverResponseCode == 202 || serverResponseCode == 200) {
            // make sure we clear the badID flag.
            SharedPreferences.Editor editor = sharedprefs.edit();
            editor.putBoolean("badID", false);
            editor.apply();
            UploadExposureService.sValidId.set(true);
        }

        return Boolean.TRUE;
    }

    private boolean canUpload() {
        return mApplication.isNetworkAvailable() && UploadExposureService.sPermitUpload.get() && UploadExposureService.sValidId.get();
    }
}
