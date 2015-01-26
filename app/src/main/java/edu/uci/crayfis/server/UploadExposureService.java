package edu.uci.crayfis.server;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.R;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by jodi on 2015-01-25.
 */
public class UploadExposureService extends IntentService {

    /**
     * Key for storing a parcelable {@link edu.uci.crayfis.exposure.ExposureBlock}.
     */
    public static final String EXPOSURE_BLOCK = "exposure_block";

    /**
     * Key for storing a serializable {@link edu.uci.crayfis.DataProtos.RunConfig}.
     */
    public static final String RUN_CONFIG = "run_config";

    /**
     * Key for storing a serializable {@link edu.uci.crayfis.DataProtos.CalibrationResult}.
     */
    public static final String CALIBRATION_RESULT = "calibration_result";

    private static final CFConfig sConfig = CFConfig.getInstance();
    private static CFApplication.AppBuild sAppBuild;
    private static ServerInfo sServerInfo;
    private static Boolean sDebugStream;

    private static boolean sPermitUpload = true;
    private static boolean sValidId = true;
    private static boolean sStartUploading;

    /**
     * Helper for submitting an {@link edu.uci.crayfis.exposure.ExposureBlock}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param exposureBlock The {@link edu.uci.crayfis.exposure.ExposureBlock}.
     */
    public static void submitExposureBlock(@NonNull final Context context, @NonNull final ExposureBlock exposureBlock) {
        final Intent intent = new Intent(context, UploadExposureService.class);
        intent.putExtra(EXPOSURE_BLOCK, exposureBlock);
        context.startService(intent);
    }

    /**
     * Helper for submitting a {@link edu.uci.crayfis.DataProtos.RunConfig}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param runConfig The {@link edu.uci.crayfis.DataProtos.RunConfig}.
     */
    public static void submitRunConfig(@NonNull final Context context, @NonNull final DataProtos.RunConfig runConfig) {
        final Intent intent = new Intent(context, UploadExposureService.class);
        intent.putExtra(RUN_CONFIG, runConfig);
        context.startService(intent);
    }

    /**
     * Helper for submitting a {@link edu.uci.crayfis.DataProtos.CalibrationResult}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param calibrationResult The {@link edu.uci.crayfis.DataProtos.CalibrationResult}.
     */
    public static void submitCalibrationResult(@NonNull final Context context,
                                               @NonNull final DataProtos.CalibrationResult calibrationResult) {
        final Intent intent = new Intent(context, UploadExposureService.class);
        intent.putExtra(CALIBRATION_RESULT, calibrationResult);
        context.startService(intent);
    }

    public UploadExposureService() {
        super("Exposure Uploader");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        lazyInit();

        final AbstractMessage message = getAbstractMessage(intent);
        if (message != null) {
            CFLog.d("Got message " + message);

            final DataProtos.DataChunk.Builder chunk = DataProtos.DataChunk.newBuilder();
            if (message instanceof DataProtos.ExposureBlock) {
                chunk.addExposureBlocks((DataProtos.ExposureBlock) message);
            } else if (message instanceof DataProtos.RunConfig) {
                chunk.addRunConfigs((DataProtos.RunConfig) message);
            } else if (message instanceof DataProtos.CalibrationResult) {
                chunk.addCalibrationResults((DataProtos.CalibrationResult) message);
            } else {
                throw new RuntimeException("Unhandled message " + message);
            }
        }
    }

    private void lazyInit() {
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
    }

    @Nullable
    private AbstractMessage getAbstractMessage(@NonNull final Intent intent) {
        final ExposureBlock exposureBlock = intent.getParcelableExtra(EXPOSURE_BLOCK);
        if (exposureBlock != null) {
            return exposureBlock.buildProto();
        }

        final AbstractMessage runConfig = (AbstractMessage) intent.getSerializableExtra(RUN_CONFIG);
        if (runConfig != null) {
            return runConfig;
        }

        final AbstractMessage calibrationResult = (DataProtos.CalibrationResult) intent.
                getSerializableExtra(CALIBRATION_RESULT);
        if (calibrationResult != null) {
            return calibrationResult;
        }

        return null;
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

    // check to see if there is a file, and if so, upload it.
    // return the number of files uploaded (currently fixed to 1 max)
    private int uploadFile() {

        File localDir = getApplicationContext().getFilesDir();
        int n_uploaded = 0;
        for (File f : localDir.listFiles()) {
            if (!canUpload()) {
                // No network connection available... nothing to do here.
                return n_uploaded;
            }

            String filename = f.getName();
            if (! filename.endsWith(".bin"))
                continue;
            String[] pieces = filename.split("_");
            if (pieces.length < 2) {
                CFLog.w("DAQActivity Skipping malformatted filename: " + filename);
                continue;
            }
            String run_id = pieces[0];
            CFLog.i("DAQActivity Found local file from run: " + run_id);

            DataProtos.DataChunk chunk;
            try {
                chunk = DataProtos.DataChunk.parseFrom(new FileInputStream(f));
            }
            catch (IOException ex) {
                CFLog.e("DAQActivity Failed to read local file!", ex);
                // TODO: should we remove the file?
                continue;
            }

            // okay, lets send the file off to upload:
            boolean uploaded = directUpload(chunk, run_id);

            if (uploaded) {
                // great! the file uploaded successfully.
                // now we can delete it from the local store.
                CFLog.i("DAQActivity Successfully uploaded local file: " + filename);
                f.delete();
                n_uploaded += 1;
                last_cache_upload = System.currentTimeMillis();
            }
            else {
                CFLog.w("DAQActivity Failed to upload file: " + filename);
            }

            break; // only try to upload one file at a time.
        }
        return n_uploaded;
    }

    // output the given data chunk, either by uploading if the network
    // is available, or (TODO: by outputting to file.)
    private void outputChunk(AbstractMessage toWrite) {
        boolean uploaded = false;
        if (canUpload()) {
            // Upload to network:
            uploaded = directUpload(toWrite, sAppBuild.getRunId().toString());
        }

        if (uploaded) {
            // Looks like everything went okay. We're done here.
            return;
        }

        // oops! network is either not available, or there was an
        // error during the upload.
        // TODO: write out to a file.
        CFLog.i("DAQActivity Unable to upload to network! Falling back to local storage.");
        int timestamp = (int) (System.currentTimeMillis()/1e3);
        String filename = sAppBuild.getRunId().toString() + "_" + timestamp + ".bin";
        //File outfile = new File(context.getFilesDir(), filename);
        FileOutputStream outputStream;
        try {
            outputStream = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE);
            toWrite.writeTo(outputStream);
            outputStream.close();
            CFLog.i("DAQActivity Data saved to " + filename);
        }
        catch (Exception ex) {
            CFLog.e("DAQActivity Error saving to file! Dropping data.", ex);
        }
    }

    private boolean directUpload(AbstractMessage toWrite, String run_id) {
        // okay, we got a writable object, let's dump it to the server!

        ByteString raw_data = toWrite.toByteString();

        CFLog.i("DAQActivity Okay! We're going to upload a chunk; it has " + raw_data.size() + " bytes");

        int serverResponseCode;

        SharedPreferences sharedprefs = PreferenceManager.
                getDefaultSharedPreferences(getApplicationContext());
        boolean success = false;
        try {
            URL u = new URL(sServerInfo.uploadUrl);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-type", "application/octet-stream");
            c.setRequestProperty("Content-length", String.format("%d", raw_data.size()));
            c.setRequestProperty("Device-id", sServerInfo.deviceId);
            c.setRequestProperty("Run-id", run_id);
            c.setRequestProperty("Crayfis-version", "a " + sServerInfo.buildVersion);
            c.setRequestProperty("Crayfis-version-code", Integer.toString(sServerInfo.versionCode));

            String app_code = sharedprefs.getString("prefUserID", "");
            if (app_code != null && ! app_code.isEmpty()) {
                c.setRequestProperty("App-code", app_code);
            }

            if (sDebugStream) {
                c.setRequestProperty("Debug-stream", "yes");
            }
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setDoOutput(true);
            c.setConnectTimeout(sServerInfo.connectTimeout);
            c.setReadTimeout(sServerInfo.readTimeout);

            OutputStream os = c.getOutputStream();

            // try writing to the output stream
            raw_data.writeTo(os);

            CFLog.i("DAQActivity Connecting to upload server at: " + sServerInfo.uploadUrl);
            c.connect();
            serverResponseCode = c.getResponseCode();
            if (serverResponseCode == 403 || serverResponseCode == 401) {
                // server rejected us! so we are not allowed to upload.
                // oh well! we can still take data at least.

                permit_upload = false;

                if (serverResponseCode == 401) {
                    // server rejected us because our app code is invalid.
                    SharedPreferences.Editor editor = sharedprefs.edit();
                    editor.putBoolean("badID", true);
                    editor.apply();
                    CFLog.w("DAQActivity setting bad ID flag!");
                    valid_id = false;
                }
                return false;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            final ServerCommand serverCommand = new Gson().fromJson(sb.toString(), ServerCommand.class);
            CFConfig.getInstance().updateFromServer(serverCommand);
            ((CFApplication) getApplicationContext()).savePreferences();

            CFLog.i("DAQActivity Connected! Status = " + serverResponseCode);

            // and now disconnect
            c.disconnect();

            if (serverResponseCode == 202 || serverResponseCode == 200) {
                // looks like everything went okay!
                success = true;
                // make sure we clear the badID flag.
                SharedPreferences.Editor editor = sharedprefs.edit();
                editor.putBoolean("badID", true);
                editor.apply();
                valid_id = true;
            }
        }
        catch (MalformedURLException ex) {
            CFLog.e("DAQActivity Oh noes! The upload url is malformed.", ex);
        }
        catch (IOException ex) {
            CFLog.e("DAQActivity Oh noes! An IOException occured.", ex);
        }
        catch (java.lang.OutOfMemoryError ex) { CFLog.e("Oh noes! An OutofMemory occured.", ex);}
        return success;
    }

    /**
     * POJO for the server information.
     */
    private static final class ServerInfo {
        private final String uploadUrl;
        private final String deviceId;
        private final String buildVersion;
        private final int versionCode;

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

            deviceId = sAppBuild.getDeviceId();
            buildVersion = sAppBuild.getBuildVersion();
            versionCode = sAppBuild.getVersionCode();
        }
    }
}
