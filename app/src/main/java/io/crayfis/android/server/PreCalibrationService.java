package io.crayfis.android.server;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.JsonWriter;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.protobuf.ByteString;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.crayfis.android.DataProtos;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.util.CFLog;

public class PreCalibrationService extends IntentService {

    private static final String EXTRA_CAMERA_ID = "camera_id";
    private static final String EXTRA_RES_X = "res_x";
    private static final String EXTRA_RES_Y = "res_y";

    private UploadExposureService.ServerInfo mServerInfo;
    private Config mConfig;

    public final static int INTER = Imgproc.INTER_CUBIC;

    public static void getWeights(Context context, int cameraId, int resX, int resY) {
        Intent intent = new Intent(context, PreCalibrationService.class)
                .putExtra(EXTRA_CAMERA_ID, cameraId)
                .putExtra(EXTRA_RES_X, resX)
                .putExtra(EXTRA_RES_Y, resY);

        context.startService(intent);
    }

    public PreCalibrationService() {
        super("PreCalibration Downloader");
    }

    @Override
    public void onHandleIntent(final Intent intent) {

        CFApplication application = (CFApplication) getApplication();
        mServerInfo = new UploadExposureService.ServerInfo(application);

        final int cameraId = intent.getIntExtra(EXTRA_CAMERA_ID, -1);
        final int resX = intent.getIntExtra(EXTRA_RES_X, 0);
        final int resY = intent.getIntExtra(EXTRA_RES_Y, 0);

        switch(downloadPrecal(cameraId, resX, resY)) {
            case 200:
            case 202:
                // use the weights and hotcells provided
                break;
            case 204:
                // the server needs a PreCalibrationResult to get started, so
                // let's try to generate one

                // but first, let's see if the problem was a redis crash
                mConfig = Config.loadFromPrefs(this, cameraId, resX, resY);
                if(mConfig != null && mConfig.isValid()) {
                    // redis must have lost the config, so re-upload it
                    ByteString weights = ByteString.copyFrom(Base64.decode(mConfig.mB64Weights, Base64.DEFAULT));
                    ArrayList<Integer> hotcells = new ArrayList<>();
                    for(int hxy: mConfig.mHotcells) {
                        hotcells.add(hxy);
                    }

                    // this is a minimal PreCalibrationResult
                    UploadExposureService.submitMessage(this, cameraId,
                            DataProtos.PreCalibrationResult.newBuilder()
                                    .setResX(resX)
                                    .setResY(resY)
                                    .setHotHash(mConfig.mHotHash)
                                    .setWgtHash(mConfig.mWeightHash)
                                    .setCompressedWeights(weights)
                                    .addAllHotcell(hotcells)
                                    .setInterpolation(INTER)
                                    .build());
                }
                break;
            default:
                // no connection to the server, so let's see if we have a recent
                // set of weights/hotcells in the SharedPrefs
                mConfig = Config.loadFromPrefs(this, cameraId, resX, resY);
        }

        if(mConfig != null && mConfig.isValid()) {
            // we have a config, so let's continue!
            CFConfig.getInstance().setPrecalConfig(mConfig);
            application.setApplicationState(CFApplication.State.CALIBRATION);
        } else {
            // calculate weights manually
            ExposureBlockManager.getInstance()
                    .newExposureBlock(CFApplication.State.PRECALIBRATION);
        }
    }

    /**
     * Connects to the server and attempts to pull weights and precalibration
     *
     * @param cameraId The integer camera ID
     * @param resX Resolution width
     * @param resY Resolution height
     * @return Status code from the server (or 0 in case of an exception)
     */
    private int downloadPrecal(int cameraId, int resX, int resY) {
        try {
            URL url = new URL(mServerInfo.precalUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-type", "application/json");
            conn.setRequestProperty("Crayfis-version", "b " + mServerInfo.buildVersion);
            conn.setRequestProperty("Crayfis-version-code", Integer.toString(mServerInfo.versionCode));

            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setDoOutput(true);
            conn.setConnectTimeout(mServerInfo.connectTimeout);
            conn.setReadTimeout(mServerInfo.readTimeout);

            // write post information as JSON
            OutputStream out = conn.getOutputStream();
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
            writer.beginObject()
                    .name("device_id").value(mServerInfo.deviceId)
                    .name("camera_id").value(cameraId)
                    .name("res").value("" + resX + "x" + resY)
                    .endObject()
                    .close();

            CFLog.i("Connecting to " + mServerInfo.precalUrl);
            conn.connect();

            final int serverResponseCode = conn.getResponseCode();
            CFLog.i("Connected with status code " + serverResponseCode);

            if(serverResponseCode == 200 || serverResponseCode == 202) {
                // read the response
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                reader.close();
                in.close();
                CFLog.d("Got response: " + sb.toString());

                mConfig = new Config(cameraId, resX, resY, new Gson().fromJson(sb.toString(), Response.class));
                if(mConfig.isValid()) {
                    CFConfig.getInstance().setPrecalConfig(mConfig);
                }
            }
            out.close();
            conn.disconnect();
            return serverResponseCode;

        } catch(IOException e) {
            e.printStackTrace();
            return 0;
        }

    }

    private static class Response {
        @SerializedName("weights") final String b64Weights;
        @SerializedName("mask") final int[] hotcells;
        @SerializedName("hot_hash") final Integer hotHash;
        @SerializedName("wgt_hash") final Integer weightHash;

        public Response(int[] hotcells, int hotHash, String b64Weights, int weightHash) {
            this.b64Weights = b64Weights;
            this.hotcells = hotcells;
            this.hotHash = hotHash;
            this.weightHash = weightHash;
        }

    }

    public static class Config {

        private final int mCameraId;
        private final int mResX;
        private final int mResY;
        private final int[] mHotcells;
        private final int mHotHash;
        private final String mB64Weights;
        private final int mWeightHash;

        private static final String KEY_WEIGHTS_TEMPLATE = "weights_%d:%dx%d";
        private static final String KEY_HOTCELLS_TEMPLATE = "hotcells_%d:%dx%d";
        private static final String KEY_HASH_TEMPLATE = "hash_%d:%dx%d";

        final String KEY_WEIGHTS;
        final String KEY_HOTCELLS;
        final String KEY_HASH;

        public Config(int cameraId, int resX, int resY, int[] hotcells, int hotHash, String b64Weights, int weightHash) {
            mCameraId = cameraId;
            mResX = resX;
            mResY = resY;
            mHotcells = hotcells;
            mHotHash = hotHash;
            mB64Weights = b64Weights;
            mWeightHash = weightHash;

            KEY_WEIGHTS = String.format(KEY_WEIGHTS_TEMPLATE, mCameraId, mResX, mResY);
            KEY_HOTCELLS = String.format(KEY_HOTCELLS_TEMPLATE, mCameraId, mResX, mResY);
            KEY_HASH = String.format(KEY_HASH_TEMPLATE, mCameraId, mResX, mResY);
        }

        public Config(int cameraId, int resX, int resY, Response resp) {
            this(cameraId, resX, resY, resp.hotcells, resp.hotHash, resp.b64Weights, resp.weightHash);
            CFLog.d("Pulled " + resp.hotcells.length + " hotcells");
        }

        public static Config fromPartialResult(int cameraId, DataProtos.PreCalibrationResult result) {
            String b64weights = Base64.encodeToString(result.getCompressedWeights().toByteArray(), Base64.DEFAULT);
            int[] hotcells = new int[result.getHotcellCount()];
            for(int i=0; i<result.getHotcellCount(); i++) {
                hotcells[i] = result.getHotcell(i);
            }
            return new Config(cameraId, result.getResX(), result.getResY(), hotcells, -1, b64weights, -1);
        }

        boolean isValid() {
            return mB64Weights != null && mHotcells != null && mHotHash >= 0 && mWeightHash >= 0;
        }

        public int getHotHash() {
            return mHotHash;
        }

        public int getWeightHash() {
            return mWeightHash;
        }

        public void saveToPrefs(SharedPreferences sharedPreferences) {
            // first convert hotcells to Set<String>
            Set<String> hexCells = new HashSet<>();
            for(int coord: mHotcells) {
                hexCells.add(Integer.toHexString(coord));
            }

            sharedPreferences.edit()
                    .putString(KEY_WEIGHTS, mB64Weights)
                    .putStringSet(KEY_HOTCELLS, hexCells)
                    .putLong(KEY_HASH, (long) mHotHash * Integer.MAX_VALUE + mWeightHash)
                    .apply();
        }

        @Nullable
        static Config loadFromPrefs(Context context, int cameraId, int resX, int resY) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String b64Weights = prefs.getString(String.format(KEY_WEIGHTS_TEMPLATE, cameraId, resX, resY), null);
            Set<String> hexCells = prefs.getStringSet(String.format(KEY_HOTCELLS_TEMPLATE, cameraId, resX, resY), null);
            Long hash = prefs.getLong(String.format(KEY_HASH_TEMPLATE, cameraId, resX, resY), -1);
            int hotHash = (int)(hash / Integer.MAX_VALUE);
            int weightHash = (int)(hash % Integer.MAX_VALUE);

            if(hotHash < 0 || weightHash < 0) return null;

            int[] hotcells = null;

            if(hexCells != null) {
                hotcells = new int[hexCells.size()];
                int i=0;
                for(String hx: hexCells) {
                    hotcells[i] = Integer.parseInt(hx, 16);
                    i++;
                }
            }

            return new Config(cameraId, resX, resY, hotcells, hotHash, b64Weights, weightHash);
        }

        public Config update(Config config) {
            if(config.mCameraId != mCameraId || config.mResX != mResX || config.mResY != mResY) {
                return this;
            }

            return new Config(mCameraId, mResX, mResY,
                    config.mHotcells != null ? config.mHotcells : mHotcells,
                    config.mHotHash >= 0 ? config.mHotHash: mHotHash,
                    config.mB64Weights != null ? config.mB64Weights : mB64Weights,
                    config.mWeightHash >= 0 ? config.mWeightHash : mWeightHash);
        }

        public ScriptC_weight getScriptCWeight(RenderScript RS) {
            // configure weights in RS as well
            byte[] weightArray;
            if(mB64Weights != null && !mB64Weights.isEmpty()) {
                byte[] bytes = Base64.decode(mB64Weights, Base64.DEFAULT);

                MatOfByte compressedMat = new MatOfByte(bytes);
                Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
                compressedMat.release();

                Mat resampledMat2D = new Mat();
                int scaleFactor = mResX / downsampleMat.rows();
                Imgproc.resize(downsampleMat, resampledMat2D, new Size(0, 0), scaleFactor, scaleFactor, INTER);
                downsampleMat.release();

                // flatten to match coordinate scheme of hotcells
                Mat resampledMat = resampledMat2D.t().reshape(0, 1);
                MatOfByte resampledByte = new MatOfByte(resampledMat);
                resampledMat2D.release();
                resampledMat.release();

                weightArray = resampledByte.toArray();
                resampledByte.release();

                resampledMat.release();
                resampledByte.release();
            } else {
                CFLog.d("No weights found");
                weightArray = new byte[mResX * mResY];
                Arrays.fill(weightArray, (byte) 255);
            }

            // kill hotcells in resampled frame;
            if(mHotcells != null) {
                for (Integer pos : mHotcells) {
                    weightArray[pos] = (byte) 0;
                }
            }

            Type weightType = new Type.Builder(RS, Element.U8(RS))
                    .setX(mResX)
                    .setY(mResY)
                    .create();
            Allocation weights = Allocation.createTyped(RS, weightType, Allocation.USAGE_SCRIPT);
            weights.copyFrom(weightArray);

            ScriptC_weight scriptCWeight = new ScriptC_weight(RS);
            scriptCWeight.set_gWeights(weights);
            return scriptCWeight;
        }
    }
}
