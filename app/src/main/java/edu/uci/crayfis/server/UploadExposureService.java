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

import com.google.protobuf.AbstractMessage;

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
