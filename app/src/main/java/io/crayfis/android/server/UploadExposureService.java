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
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
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
 * This does not perform any uploading itself.  It instead validates that a valid block has been
 * received, write to the cache and execute a {@link io.crayfis.android.server.UploadExposureTask}.
 */
public class UploadExposureService extends IntentService {

    public static final AtomicBoolean sPermitUpload = new AtomicBoolean(true);
    public static final AtomicBoolean sValidId = new AtomicBoolean(true);

    public static final String EXTRA_CAMERA_ID = "camera_id";

    public static final String EXTRA_UPLOAD_CACHE = "upload_cache";

    public static final String PROTOBUF_MESSAGE = "pb_message";

    private CFApplication.AppBuild mAppBuild;
    private int mCameraId;
    private boolean mIsPublic;
    private File mPath;
    private ServerInfo mServerInfo;

    private static long sLastCacheUpload;
    private static final long UPLOAD_CACHE_GAP = 5000L;
    private static final long UPLOAD_CACHE_SPACING = 200L;

    /**
     * Helper for submitting one of the fields of a  {@link io.crayfis.android.DataProtos.DataChunk}.
     *
     * This will create a new Intent and call startService with that intent.
     *
     * @param context The context for the intent.
     * @param message The {@link io.crayfis.android.exposure.ExposureBlock}.
     */
    public static void submitMessage(@NonNull final Context context,
                                           @NonNull final Integer cameraId,
                                           @NonNull final GeneratedMessageV3 message) {

        try {
            final Intent intent = new Intent(context, UploadExposureService.class)
                    .putExtra(PROTOBUF_MESSAGE, message)
                    .putExtra(EXTRA_CAMERA_ID, cameraId);
            context.startService(intent);
        } catch (RuntimeException e) {
            // don't crash if an XB is too big
            if(message instanceof DataProtos.ExposureBlock) {
                final Intent intent = new Intent(context, UploadExposureService.class);
                // still pass an XB to the server to report the error
                final DataProtos.ExposureBlock xb = (DataProtos.ExposureBlock) message;
                intent.putExtra(PROTOBUF_MESSAGE,
                        DataProtos.ExposureBlock.newBuilder(xb)
                        .addAllEvents(null)
                        .build());
            }
        }
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

        if(intent.getBooleanExtra(EXTRA_UPLOAD_CACHE, false)) {
            // upload everything in the file directory
            final List<File> cache = new LinkedList<>(Arrays.asList(getApplicationContext().getFilesDir().listFiles()));
            Timer timer = new Timer();
            TimerTask uploadTask = new TimerTask() {
                @Override
                public void run() {
                    if(cache.isEmpty()) {
                        this.cancel();
                        return;
                    }
                    File f = cache.remove(0);
                    if(f.getName().endsWith(".tmp.bin")) {
                        String completeFilename = f.getName().replace(".tmp.bin", ".bin");
                        File completeFile = new File(mPath, completeFilename);
                        CFLog.d("New file name: " + completeFilename);
                        if (!f.renameTo(completeFile)) {
                            CFLog.w("Failed to rename file " + f.getName());
                        }
                        uploadFile(completeFile);
                    } else if(f.getName().endsWith(".bin")) {
                        uploadFile(f);
                    }
                }
            };

            timer.scheduleAtFixedRate(uploadTask, 0, UPLOAD_CACHE_SPACING);

        } else {
            // otherwise, make a file from protobuf data
            final AbstractMessage message = (AbstractMessage) intent.getSerializableExtra(PROTOBUF_MESSAGE);
            mCameraId = intent.getIntExtra(EXTRA_CAMERA_ID, -1);
            if (message != null) {
                CFLog.d("Got message " + message);
                final DataProtos.DataChunk.Builder builder = addMessageToDataChunk(message);
                if (builder != null) {
                    final AbstractMessage uploadMessage = builder.build();
                    File file = saveMessageToCache(uploadMessage);
                    if(mIsPublic) {
                        // make sure we save things like precalibration result
                        CFApplication application = (CFApplication) this.getApplication();
                        application.savePreferences();
                    } else if(file != null) {
                        uploadFile(file);
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
            public void run() {new UploadExposureTask(application, mServerInfo, file).
                    execute();
            }
        });

    }

    @NonNull
    private DataProtos.DataChunk.Builder getCachedDataChunk() {

        // first, see if we have an incomplete file cached
        File[] incompleteFiles = mPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".tmp.bin");
            }
        });

        for (File f : incompleteFiles) {
            CFLog.d("Found a file with length " + f.length());
            String[] pieces = f.getName().split("_");
            if (mAppBuild.getRunId().toString().equals(pieces[0])
                    && Integer.toString(mCameraId).equals(pieces[1])
                    && f.length() < CFConfig.getInstance().getDataChunkSize()) {
                try {
                    final FileInputStream inputStream = new FileInputStream(f);
                    DataProtos.DataChunk dc = DataProtos.DataChunk.parseFrom(inputStream);
                    inputStream.close();
                    if (!f.delete()) {
                        CFLog.w("Could not delete file " + f.getName());
                    }
                    return dc.toBuilder();
                } catch (Exception e) {
                    CFLog.e("Error opening file " + f.getName());
                    e.printStackTrace();
                }
            } else {
                CFLog.d("File ready for upload: " + f.getName());
                String completeFilename = f.getName().replace(".tmp.bin", ".bin");
                File completeFile = new File(mPath, completeFilename);
                CFLog.d("New file name: " + completeFilename);
                if (!f.renameTo(completeFile)) {
                    CFLog.w("Failed to rename file " + f.getName());
                }
                if(!mIsPublic) uploadFile(completeFile);
            }
        }

        // if not, we make a new builder
        return DataProtos.DataChunk.newBuilder();

    }

    /**
     * Add message to existing DataChunk if one exists
     *
     * This will return an instance of {@link io.crayfis.android.DataProtos.DataChunk.Builder}
     * if the right message has been received.
     *
     * @param message The incoming {@link com.google.protobuf.AbstractMessage}.
     * @return An instance of {@link io.crayfis.android.DataProtos.DataChunk.Builder} or {@code null}.
     */
    @Nullable
    private DataProtos.DataChunk.Builder addMessageToDataChunk(AbstractMessage message) {

        DataProtos.DataChunk.Builder rtn = getCachedDataChunk();

        if (message instanceof DataProtos.ExposureBlock) {
            rtn.addExposureBlocks((DataProtos.ExposureBlock) message);
        } else if (message instanceof DataProtos.RunConfig) {
            rtn.addRunConfigs((DataProtos.RunConfig) message);
        } else if (message instanceof DataProtos.CalibrationResult) {
            rtn.addCalibrationResults((DataProtos.CalibrationResult) message);
        } else if (message instanceof DataProtos.PreCalibrationResult) {
            rtn.addPrecalibrationResults((DataProtos.PreCalibrationResult) message);
        } else {
            return null;
        }

        return rtn;
    }

    private void lazyInit() {
        final CFApplication context = (CFApplication) getApplicationContext();
        // need to see whether this has changed
        mAppBuild = context.getBuildInformation();

        if (mServerInfo == null) {
            mServerInfo = new ServerInfo(context);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mIsPublic = prefs.getBoolean(getString(R.string.prefStorePublic), false);
        mPath = !mIsPublic ? context.getFilesDir()
                : new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),"CRAYFIS");
        if(mIsPublic) mPath.mkdir();
    }

    @Nullable
    private File saveMessageToCache(final AbstractMessage abstractMessage) {
        final boolean temp = abstractMessage.getSerializedSize() < CFConfig.getInstance().getDataChunkSize();
        final long timestamp = System.currentTimeMillis();
        final String filename = mAppBuild.getRunId().toString() + "_" + mCameraId + "_" + timestamp
                + (temp ? ".tmp.bin" : ".bin");
        final File protofile = new File(mPath, filename);;

        final FileOutputStream outputStream;
        try {
            if(mIsPublic) {
                outputStream = new FileOutputStream(protofile);
            } else {
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

        return temp ? null : protofile;
    }

    /**
     * POJO for the server information.
     */
    public static final class ServerInfo {
         final String uploadUrl;
         final String precalUrl;
         String deviceId;
         String buildVersion;
         int versionCode;

        final int connectTimeout = 2 * 1000; // ms
        final int readTimeout = 5 * 1000; // ms

        ServerInfo(@NonNull final Context context) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final String ipAddress = prefs.getString(context.getString(R.string.prefIpAddress), "");

            final String uploadUri = context.getString(R.string.upload_uri);
            final String precalUri = context.getString(R.string.precal_uri);

            if(ipAddress.isEmpty()) {

                final String serverAddress = context.getString(R.string.server_address);
                final String serverPort = context.getString(R.string.server_port);
                final boolean forceHttps = context.getResources().getBoolean(R.bool.force_https);

                uploadUrl = String.format("%s://%s:%s%s",
                        forceHttps ? "https" : "http",
                        serverAddress,
                        serverPort,
                        uploadUri);

                precalUrl = String.format("%s://%s:%s%s",
                        forceHttps ? "https" : "http",
                        serverAddress,
                        serverPort,
                        precalUri);

            } else {
                uploadUrl = String.format("http://%s%s", ipAddress, uploadUri);
                precalUrl = String.format("http://%s%s", ipAddress, precalUri);
            }

            CFApplication application = (CFApplication) context.getApplicationContext();
            CFApplication.AppBuild build = application.getBuildInformation();
            if (build != null) {
                deviceId = build.getDeviceId();
                buildVersion = build.getBuildVersion();
                versionCode = build.getVersionCode();
            }
        }
    }
}
