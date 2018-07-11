package io.crayfis.android.server;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.AbstractMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.util.CFLog;

/**
 * An implementation of IntentService that handles uploading blocks to the server.
 *
 * You can use the helper methods {@link #submitCalibrationResult(android.content.Context, java.lang.Integer, io.crayfis.android.DataProtos.CalibrationResult)},
 * {@link #submitExposureBlock(android.content.Context, java.lang.Integer, io.crayfis.android.DataProtos.ExposureBlock)} or
 * {@link #submitRunConfig(android.content.Context, java.lang.Integer, io.crayfis.android.DataProtos.RunConfig)} to make this
 * easier to use.
 *
 * This does not perform any uploading itself.  It instead validates that a valid block has been
 * received, write to the cache and execute a {@link io.crayfis.android.server.UploadExposureTask}.
 */
public class UploadExposureService extends IntentService {

    public static final AtomicBoolean sPermitUpload = new AtomicBoolean(true);
    public static final AtomicBoolean sValidId = new AtomicBoolean(true);

    public static final String EXTRA_CAMERA_ID = "camera_id";

    public static final String EXTRA_UPLOAD_CACHE = "upload_cache";

    /**
     * Key for storing a parcelable {@link io.crayfis.android.exposure.ExposureBlock}.
     */
    public static final String EXPOSURE_BLOCK = "exposure_block";

    /**
     * Key for storing a serializable {@link io.crayfis.android.DataProtos.RunConfig}.
     */
    public static final String RUN_CONFIG = "run_config";

    /**
     * Key for storing a serializable {@link io.crayfis.android.DataProtos.PreCalibrationResult}.
     */
    public static final String PRECALIBRATION_RESULT = "precalibration_result";

    /**
     * Key for storing a serializable {@link io.crayfis.android.DataProtos.CalibrationResult}.
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

    private static CFApplication.AppBuild sAppBuild;
    private static ServerInfo sServerInfo;

    private static long sLastCacheUpload;
    private static final long UPLOAD_CACHE_GAP = 5000L;

    /**
     * Helper for submitting an {@link io.crayfis.android.exposure.ExposureBlock}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param exposureBlock The {@link io.crayfis.android.exposure.ExposureBlock}.
     */
    public static void submitExposureBlock(@NonNull final Context context,
                                           @NonNull final Integer cameraId,
                                           @NonNull final DataProtos.ExposureBlock exposureBlock) {

        try {
            final Intent intent = new Intent(context, UploadExposureService.class)
                    .putExtra(EXPOSURE_BLOCK, exposureBlock)
                    .putExtra(EXTRA_CAMERA_ID, cameraId);
            context.startService(intent);
        } catch (RuntimeException e) {
            // don't crash if the XB is too big
            final Intent intent = new Intent(context, UploadExposureService.class);
            // still pass an XB to the server to report the error
            intent.putExtra(EXPOSURE_BLOCK, DataProtos.ExposureBlock.newBuilder(exposureBlock)
                    .addAllEvents(null)
                    .build());

        }
    }

    /**
     * Helper for submitting a {@link io.crayfis.android.DataProtos.RunConfig}.  You should be sure
     * to call this before submitting your first exposure block.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param runConfig The {@link io.crayfis.android.DataProtos.RunConfig}.
     */
    public static void submitRunConfig(@NonNull final Context context,
                                       @NonNull final Integer cameraId,
                                       @NonNull final DataProtos.RunConfig runConfig) {
        final Intent intent = new Intent(context, UploadExposureService.class)
                .putExtra(RUN_CONFIG, runConfig)
                .putExtra(EXTRA_CAMERA_ID, cameraId);
        context.startService(intent);
    }

    /**
     * Helper for submitting a {@link io.crayfis.android.DataProtos.CalibrationResult}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param calibrationResult The {@link io.crayfis.android.DataProtos.CalibrationResult}.
     */
    public static void submitCalibrationResult(@NonNull final Context context,
                                               @NonNull final Integer cameraId,
                                               @NonNull final DataProtos.CalibrationResult calibrationResult) {
        final Intent intent = new Intent(context, UploadExposureService.class)
                .putExtra(CALIBRATION_RESULT, calibrationResult)
                .putExtra(EXTRA_CAMERA_ID, cameraId);
        context.startService(intent);
    }

    /**
     * Helper for submitting a {@link io.crayfis.android.DataProtos.PreCalibrationResult}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param preCalibrationResult The {@link io.crayfis.android.DataProtos.PreCalibrationResult}.
     */
    public static void submitPreCalibrationResult(@NonNull final Context context,
                                               @NonNull final Integer cameraId,
                                               @NonNull final DataProtos.PreCalibrationResult preCalibrationResult) {

        final Intent intent = new Intent(context, UploadExposureService.class)
                .putExtra(PRECALIBRATION_RESULT, preCalibrationResult)
                .putExtra(EXTRA_CAMERA_ID, cameraId);
        context.startService(intent);
    }

    public synchronized static void uploadFileCache(@NonNull final Context context) {

        // make sure this isn't called again before the files can upload
        if(System.currentTimeMillis() - sLastCacheUpload < UPLOAD_CACHE_GAP) return;
        sLastCacheUpload = System.currentTimeMillis();

        final Intent intent = new Intent(context, UploadExposureService.class);
        intent.putExtra(EXTRA_UPLOAD_CACHE, true);
        context.startService(intent);

    }

    public UploadExposureService() {
        super("Exposure Uploader");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        lazyInit();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean isPublic = prefs.getBoolean(getString(R.string.prefStorePublic), false);

        if(intent.getBooleanExtra(EXTRA_UPLOAD_CACHE, false)) {
            // upload everything in the file directory
            Context context = getApplicationContext();
            final List<File> cache = new LinkedList<>(Arrays.asList(context.getFilesDir().listFiles()));
            final long spacing = 200L;
            Timer timer = new Timer();
            TimerTask uploadTask = new TimerTask() {
                @Override
                public void run() {
                    if(cache.isEmpty()) {
                        this.cancel();
                        return;
                    }
                    File f = cache.remove(0);
                    if(f.getName().endsWith(".bin")) {
                        uploadFile(f);
                    }
                }
            };

            timer.scheduleAtFixedRate(uploadTask, 0, spacing);

        } else {
            // otherwise, make a file from protobuf data
            final AbstractMessage message = getAbstractMessage(intent);
            final Integer cameraId = intent.getIntExtra(EXTRA_CAMERA_ID, -1);
            if (message != null) {
                CFLog.d("Got message " + message);
                final DataProtos.DataChunk.Builder builder = createDataChunk(message);
                if (builder != null) {
                    if (sPendingRunConfig != null) {
                        submitRunConfig(getApplicationContext(), cameraId, sPendingRunConfig);
                    }
                    final AbstractMessage uploadMessage = builder.build();
                    File file = saveMessageToCache(cameraId, uploadMessage, isPublic);
                    if(file != null) {
                        if(isPublic) {
                            // make sure we save things like precalibration result
                            CFApplication application = (CFApplication) this.getApplication();
                            application.savePreferences();
                        } else {
                            uploadFile(file);
                        }
                    }
                }
            }
        }

    }

    /**
     * Uploads file to server
     *
     * @param uploadFile .bin file to be processed
     */
    private void uploadFile(@NonNull File uploadFile) {

        CFLog.d("Queueing upload task");
        final CFApplication application = (CFApplication) getApplication();
        final File file = uploadFile;

        // need to call execute() on UI thread
        Handler handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {new UploadExposureTask(application, sServerInfo, file).
                    execute();
            }
        });

    }

    /**
     * Create the data chunk.
     *
     * This will return an instance of {@link io.crayfis.android.DataProtos.DataChunk.Builder}
     * if the right message has been received.  In the case of {@link io.crayfis.android.DataProtos.RunConfig},
     * if {@link #sPendingRunConfig} is null, it will be saved and {@code null} returned.  Otherwise,
     * it will be placed in the builder and {@link #sPendingRunConfig} cleared.
     *
     * @param message The incoming {@link com.google.protobuf.AbstractMessage}.
     * @return An instance of {@link io.crayfis.android.DataProtos.DataChunk.Builder} or {@code null}.
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
        } else if (message instanceof DataProtos.PreCalibrationResult) {
            rtn.addPrecalibrationResults((DataProtos.PreCalibrationResult) message);
        }

        return rtn;
    }

    private void lazyInit() {
        final CFApplication context = (CFApplication) getApplicationContext();
        // need to see whether this has changed
        sAppBuild = context.getBuildInformation();

        if (sServerInfo == null) {
            sServerInfo = new ServerInfo(context);
        }
    }

    @Nullable
    private AbstractMessage getAbstractMessage(@NonNull final Intent intent) {

        final AbstractMessage exposureBlock = (AbstractMessage) intent.getSerializableExtra(EXPOSURE_BLOCK);
        if (exposureBlock != null) {
            return exposureBlock;
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

        final AbstractMessage preCalibrationResult = (DataProtos.PreCalibrationResult) intent.
                getSerializableExtra(PRECALIBRATION_RESULT);
        if (preCalibrationResult != null) {
            return preCalibrationResult;
        }
        return null;
    }

    @Nullable
    private File saveMessageToCache(final int cameraId, final AbstractMessage abstractMessage, boolean isPublic) {
        final long timestamp = System.currentTimeMillis();
        final String type = getDataChunkType(abstractMessage);
        final String filename = sAppBuild.getRunId().toString() + "_" + cameraId + "_" + timestamp + "." + type + ".bin";
        File protofile;
        FileOutputStream outputStream;

        try {
            if(isPublic) {
                File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),"CRAYFIS");
                path.mkdir();
                    protofile = new File(path, filename);
                    outputStream = new FileOutputStream(protofile);
            } else {
                protofile = new File(getApplicationContext().getFilesDir(), filename);
                outputStream = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE);
            }
            abstractMessage.writeTo(outputStream);
            outputStream.close();
            CFLog.i("Data saved to " + filename);
        }
        catch (Exception ex) {
            CFLog.e("Error saving to file! Dropping data.", ex);
            return null;
        }

        return protofile;
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
            } else if (chunk.getPrecalibrationResultsCount() > 0) {
                rtn = "PreCalibration";
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
         String uploadUrl;
         String deviceId;
         String buildVersion;
         int versionCode;

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

            if (sAppBuild != null) {
                deviceId = sAppBuild.getDeviceId();
                buildVersion = sAppBuild.getBuildVersion();
                versionCode = sAppBuild.getVersionCode();
            }
        }
    }
}
