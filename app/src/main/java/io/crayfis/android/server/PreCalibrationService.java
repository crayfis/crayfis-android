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
import android.util.Base64;
import android.util.JsonWriter;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.util.CFLog;

public class PreCalibrationService extends IntentService {

    private static final String EXTRA_CAMERA_ID = "camera_id";
    private static final String EXTRA_RES_X = "res_x";
    private static final String EXTRA_RES_Y = "res_y";

    private UploadExposureService.ServerInfo mServerInfo;
    private PreCalibrationConfig mConfig;

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
            case 204:
                // the server needs a PreCalibrationResult to get started, so
                // let's try to generate one
                break;
            default:
                // no connection to the server, so let's see if we have a recent
                // set of weights/hotcells in the SharedPrefs
                mConfig = PreCalibrationConfig.loadFromPrefs(this, cameraId, resX, resY);
        }

        if(mConfig != null && mConfig.isValid()) {

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
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                CFLog.d("Got response: " + sb.toString());

                mConfig = new Gson().fromJson(sb.toString(), PreCalibrationConfig.class);
                if(mConfig.isValid()) {
                    mConfig.saveToPrefs(this, cameraId, resX, resY);
                }
            }

            conn.disconnect();
            return serverResponseCode;

        } catch(IOException e) {
            e.printStackTrace();
            return 0;
        }

    }


    public static class PreCalibrationConfig {

        private static final String KEY_WEIGHTS = "weights_%d:%dx%d";
        private static final String KEY_HOTCELLS = "hotcells_%d:%dx%d";
        private static final String KEY_PRECAL_ID = "id_%d:%dx%d";

        @SerializedName("weights") private final String b64Weights;
        @SerializedName("mask") private final int[] hotcells;
        @SerializedName("precal_id") private final String precalId;

        public PreCalibrationConfig(String b64Weights, int[] hotcells, String precalId) {
            this.b64Weights = b64Weights;
            this.hotcells = hotcells;
            this.precalId = precalId;
        }

        boolean isValid() {
            return b64Weights != null && hotcells != null && precalId != null;
        }

        public void saveToPrefs(Context context, int cameraId, int resX, int resY) {
            // first convert hotcells to Set<String>
            Set<String> hexCells = new HashSet<>();
            for(int coord: hotcells) {
                hexCells.add(Integer.toHexString(coord));
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit()
                    .putString(String.format(KEY_WEIGHTS, cameraId, resX, resY), b64Weights)
                    .putStringSet(String.format(KEY_HOTCELLS, cameraId, resX, resY), hexCells)
                    .putString(String.format(KEY_PRECAL_ID, cameraId, resX, resY), precalId)
                    .apply();
        }

        static PreCalibrationConfig loadFromPrefs(Context context, int cameraId, int resX, int resY) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String b64Weights = prefs.getString(String.format(KEY_WEIGHTS, cameraId, resX, resY), null);
            Set<String> hexCells = prefs.getStringSet(String.format(KEY_HOTCELLS, cameraId, resX, resY), null);
            String precalId = prefs.getString(String.format(KEY_PRECAL_ID, cameraId, resX, resY), null);

            int[] hotcells = null;

            if(hexCells != null) {
                hotcells = new int[hexCells.size()];
                int i=0;
                for(String hx: hexCells) {
                    hotcells[i] = Integer.parseInt(hx, 16);
                    i++;
                }
            }

            return new PreCalibrationConfig(b64Weights, hotcells, precalId);
        }

        public String getB64Weights() {
            return b64Weights;
        }

        public int[] getHotcells() {
            return hotcells;
        }

        public String getPrecalId() {
            return precalId;
        }

        public UUID getPrecalUUID() {
            return UUID.fromString(String.format("%s-%s-%s-%s-%s",
                    precalId.substring(0, 8),
                    precalId.substring(8, 12),
                    precalId.substring(12, 16),
                    precalId.substring(16, 20),
                    precalId.substring(20)));
        }

        public ScriptC_weight getScriptCWeight(RenderScript RS, int resX, int resY) {
            // configure weights in RS as well
            byte[] bytes = Base64.decode(b64Weights, Base64.DEFAULT);

            MatOfByte compressedMat = new MatOfByte(bytes);
            Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
            compressedMat.release();

            Mat resampledMat2D = new Mat();
            Imgproc.resize(downsampleMat, resampledMat2D, new Size(resX, resY), 0, 0, INTER);
            downsampleMat.release();

            Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
            MatOfByte resampledByte = new MatOfByte(resampledMat);
            resampledMat2D.release();
            resampledMat.release();

            byte[] resampledArray = resampledByte.toArray();
            resampledByte.release();

            // kill hotcells in resampled frame;
            if(hotcells != null) {
                for (Integer pos : hotcells) {
                    resampledArray[pos] = (byte) 0;
                }
            }

            Type weightType = new Type.Builder(RS, Element.U8(RS))
                    .setX(resX)
                    .setY(resY)
                    .create();
            Allocation weights = Allocation.createTyped(RS, weightType, Allocation.USAGE_SCRIPT);
            weights.copyFrom(resampledArray);

            resampledMat.release();
            resampledByte.release();

            ScriptC_weight scriptCWeight = new ScriptC_weight(RS);
            scriptCWeight.set_gWeights(weights);
            return scriptCWeight;
        }
    }
}
