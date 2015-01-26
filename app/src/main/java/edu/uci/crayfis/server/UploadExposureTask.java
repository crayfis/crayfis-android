package edu.uci.crayfis.server;

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

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by username on 1/26/2015.
 */
public class UploadExposureTask extends AsyncTask<Object, Object, Boolean> {

    private final CFApplication mApplication;
    private final UploadExposureService.ServerInfo mServerInfo;
    private final String mRunId;
    private final File mFile;

    public UploadExposureTask(@NonNull final CFApplication application,
                              @NonNull final UploadExposureService.ServerInfo serverInfo,
                              @NonNull final String runId,
                              @NonNull final File file) {
        mApplication = application;
        mServerInfo = serverInfo;
        mRunId = runId;
        mFile = file;
    }

    @Override
    protected Boolean doInBackground(final Object... objects) {

        try {
            final DataProtos.DataChunk chunk = DataProtos.DataChunk.
                    parseFrom(new FileInputStream(mFile));
            final ByteString rawData = chunk.toByteString();

            CFLog.i("DAQActivity Okay! We're going to upload a chunk; it has " +
                    rawData.size() + " bytes");

            uploadData(rawData);
        } catch (IOException ex) {
            CFLog.e("Unable to upload file " + mFile, ex);
            mFile.delete();
        } catch (OutOfMemoryError ex) {
            // FIXME Why was this even running out of memory?
            CFLog.e("Oh noes! An OutofMemory occured.", ex);
        }

        return Boolean.TRUE;
    }

    private final Boolean uploadData(@NonNull final ByteString rawData) throws IOException {
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

        CFLog.i("DAQActivity Connecting to upload server at: " + mServerInfo.uploadUrl);
        c.connect();

        final int serverResponseCode = c.getResponseCode();

        if (serverResponseCode == 403 || serverResponseCode == 401) {
            // server rejected us! so we are not allowed to upload.
            // oh well! we can still take data at least.

            // TODO There was a permit_upload variable here...

            if (serverResponseCode == 401) {
                // server rejected us because our app code is invalid.
                SharedPreferences.Editor editor = sharedprefs.edit();
                editor.putBoolean("badID", true);
                editor.apply();
                CFLog.w("DAQActivity setting bad ID flag!");

                // TODO There was a valid_id variable here, it's used in DAQActivity.
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

        CFLog.i("DAQActivity Connected! Status = " + serverResponseCode);

        // and now disconnect
        c.disconnect();

        if (serverResponseCode == 202 || serverResponseCode == 200) {
            // make sure we clear the badID flag.
            SharedPreferences.Editor editor = sharedprefs.edit();
            editor.putBoolean("badID", false);
            editor.apply();

            // TODO There was a valid_id variable here, it's used in DAQActivity.
        }

        return Boolean.TRUE;
    }
}
