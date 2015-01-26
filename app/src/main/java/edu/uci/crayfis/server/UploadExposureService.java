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
    public static void submitExposureBlock(@NonNull final Context context,
                                           @NonNull final ExposureBlock exposureBlock) {
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
    public static void submitRunConfig(@NonNull final Context context,
                                       @NonNull final DataProtos.RunConfig runConfig) {
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
            final AbstractMessage uploadMessage = createDataChunk(message).build();
            final File file = saveMessageToCache(uploadMessage);
            if (file != null) {
                new UploadExposureTask((CFApplication) getApplicationContext(),
                        sServerInfo,
                        sAppBuild.getRunId().toString(),
                        file).execute();
            }
        }
    }

    private DataProtos.DataChunk.Builder createDataChunk(final AbstractMessage message) {
        final DataProtos.DataChunk.Builder rtn = DataProtos.DataChunk.newBuilder();
        if (message instanceof DataProtos.ExposureBlock) {
            rtn.addExposureBlocks((DataProtos.ExposureBlock) message);
        } else if (message instanceof DataProtos.RunConfig) {
            rtn.addRunConfigs((DataProtos.RunConfig) message);
        } else if (message instanceof DataProtos.CalibrationResult) {
            rtn.addCalibrationResults((DataProtos.CalibrationResult) message);
        } else {
            throw new RuntimeException("Unhandled message " + message);
        }
        return rtn;
    }

    private void lazyInit() {
        final CFApplication context = (CFApplication) getApplicationContext();
        if (sAppBuild == null) {
            sAppBuild = context.getBuildInformation();
        }
        if (sServerInfo == null) {
            sServerInfo = new ServerInfo(context);
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
        if (canUpload() && directUpload(toWrite, sAppBuild.getRunId().toString())) {
            return;
        }

        CFLog.i("DAQActivity Unable to upload to network! Falling back to local storage.");
        saveMessageToCache(toWrite);
    }

    @Nullable
    private File saveMessageToCache(final AbstractMessage abstractMessage) {
        int timestamp = (int) (System.currentTimeMillis()/1e3);
        String filename = sAppBuild.getRunId().toString() + "_" + timestamp + ".bin";
        FileOutputStream outputStream;

        try {
            outputStream = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE);
            abstractMessage.writeTo(outputStream);
            outputStream.close();
            CFLog.i("DAQActivity Data saved to " + filename);
        }
        catch (Exception ex) {
            CFLog.e("DAQActivity Error saving to file! Dropping data.", ex);
            return null;
        }

        return new File(getApplicationContext().getFilesDir().toString() + filename);
    }

    /**
     * POJO for the server information.
     */
    static final class ServerInfo {
        final String uploadUrl;
        final String deviceId;
        final String buildVersion;
        final int versionCode;

        final int connectTimeout = 2 * 1000; // ms
        final int readTimeout = 5 * 1000; // ms

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
