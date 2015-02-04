package edu.uci.crayfis.server;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.AbstractMessage;

import java.io.File;
import java.io.FileOutputStream;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.R;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * An implementation of IntentService that handles uploading blocks to the server.
 *
 * You can use the helper methods {@link #submitCalibrationResult(android.content.Context, edu.uci.crayfis.DataProtos.CalibrationResult)},
 * {@link #submitExposureBlock(android.content.Context, edu.uci.crayfis.exposure.ExposureBlock)} or
 * {@link #submitRunConfig(android.content.Context, edu.uci.crayfis.DataProtos.RunConfig)} to make this
 * easier to use.
 *
 * This does not perform any uploading itself.  It instead validates that a valid block has been
 * received, write to the cache and execute a {@link edu.uci.crayfis.server.UploadExposureTask}.
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

    /**
     * A RunConfig is only to be uploaded if an ExposureBlock or CalibrationResult has been received.
     *
     * The run config is tied to the lifespan of CFApplication.  As such, this will
     * be reset when a new RunConfiguration is generated after the instance of
     * CFApplication no longer exists because this will no longer exist either.
     */
    @Nullable
    private static DataProtos.RunConfig sPendingRunConfig;

    private static boolean sReceivedExposureBlock;

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
     * Helper for submitting a {@link edu.uci.crayfis.DataProtos.RunConfig}.  You should be sure
     * to call this before submitting your first exposure block.
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
            final DataProtos.DataChunk.Builder builder = createDataChunk(message);
            if (builder != null) {
                if (sPendingRunConfig != null) {
                    submitRunConfig(getApplicationContext(), sPendingRunConfig);
                }
                final AbstractMessage uploadMessage = builder.build();
                final File file = saveMessageToCache(uploadMessage);
                if (file != null) {
                    CFLog.d("Queueing upload task");
                    new UploadExposureTask((CFApplication) getApplicationContext(), sServerInfo, file).
                            execute();
                }
            }
        }
    }

    /**
     * Create the data chunk.
     *
     * This will return an instance of {@link edu.uci.crayfis.DataProtos.DataChunk.Builder}
     * if the right message has been received.  In the case of {@link edu.uci.crayfis.DataProtos.RunConfig},
     * if {@link #sPendingRunConfig} is null, it will be saved and {@code null} returned.  Otherwise,
     * it will be placed in the builder and {@link #sPendingRunConfig} cleared.
     *
     * @param message The incoming {@link com.google.protobuf.AbstractMessage}.
     * @return An instance of {@link edu.uci.crayfis.DataProtos.DataChunk.Builder} or {@code null}.
     */
    @Nullable
    private DataProtos.DataChunk.Builder createDataChunk(final AbstractMessage message) {
        final DataProtos.DataChunk.Builder rtn = DataProtos.DataChunk.newBuilder();
        if (message instanceof DataProtos.ExposureBlock) {
            CFLog.d("Received an exposure block.");
            rtn.addExposureBlocks((DataProtos.ExposureBlock) message);
        } else if (message instanceof DataProtos.RunConfig) {
            if (sPendingRunConfig == null) {
                CFLog.d("Received a run configuration.  Keeping a reference for now.");
                sPendingRunConfig = (DataProtos.RunConfig) message;
                return null;
            } else {
                CFLog.d("Using previously referenced run config.");
                rtn.addRunConfigs(sPendingRunConfig);
                sPendingRunConfig = null;
            }
        } else if (message instanceof DataProtos.CalibrationResult) {
            rtn.addCalibrationResults((DataProtos.CalibrationResult) message);
            // FIXME: This doesn't trigger the run config upload.
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

    @Nullable
    private File saveMessageToCache(final AbstractMessage abstractMessage) {
        int timestamp = (int) (System.currentTimeMillis()/1e3);
        final String type = getDataChunkType(abstractMessage);
        String filename = sAppBuild.getRunId().toString() + "_" + timestamp + "." + type + ".bin";
        FileOutputStream outputStream;

        try {
            outputStream = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE);
            abstractMessage.writeTo(outputStream);
            outputStream.close();
            CFLog.i("Data saved to " + filename);
        }
        catch (Exception ex) {
            CFLog.e("Error saving to file! Dropping data.", ex);
            return null;
        }

        return new File(getApplicationContext().getFilesDir().toString() + "/" + filename);
    }

    private String getDataChunkType(final AbstractMessage abstractMessage) {
        final String rtn;
        if (! (abstractMessage instanceof DataProtos.DataChunk)) {
            rtn = "UNKNOWN";
        } else {
            final DataProtos.DataChunk chunk = (DataProtos.DataChunk) abstractMessage;
            if (chunk.getCalibrationResultsCount() > 0) {
                rtn = "Calibration";
            } else if (chunk.getRunConfigsCount() > 0) {
                rtn = "RunConfig";
            } else if (chunk.getExposureBlocksCount() > 0) {
                rtn = "Exposure";
            } else {
                rtn = "UNKNOWN";
            }
        }
        return rtn;
    }

    /**
     * POJO for the server information.
     */
    public static final class ServerInfo {
        final String uploadUrl;
        final String deviceId;
        final String buildVersion;
        final int versionCode;

        final int connectTimeout = 2 * 1000; // ms
        final int readTimeout = 5 * 1000; // ms

        public ServerInfo(@NonNull final Context context) {
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
