package io.crayfis.android.trigger.precalibration;

import android.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.DataProtos;
import io.crayfis.android.server.PreCalibrationService;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator extends TriggerProcessor {

    public static final String KEY_SECOND_MAX_THRESH = "second_max_thresh";
    public static final String KEY_HOTCELL_LIMIT = "hotcell_thresh";
    public static final String KEY_WEIGHT_GRID_SIZE = "grid_size";

    public static class ConfigList extends ArrayList<Config> {

        @Override
        public String toString() {
            if(size() == 0) return "";
            StringBuilder sb = new StringBuilder();
            for(Config config : this) {
                sb.append(config)
                        .append(" -> ");
            }
            String cfgStr = sb.toString();
            return cfgStr.substring(0, cfgStr.length()-4);
        }
    }

    private static List<Config> sConfigList;
    private static int sConfigStep = 0;
    static final DataProtos.PreCalibrationResult.Builder BUILDER = DataProtos.PreCalibrationResult.newBuilder();

    private PreCalibrator(CFApplication app, ExposureBlock xb, Config config) {
        super(app, xb, config, false);
    }

    public static TriggerProcessor makeProcessor(CFApplication application, ExposureBlock xb) {
        if(sConfigStep == 0) {
            BUILDER.clear();
            sConfigList = CFConfig.getInstance().getPrecalTrigger();
        }

        return new PreCalibrator(application, xb, sConfigList.get(sConfigStep));
    }

    public static ConfigList makeConfig(String configStr) {

        String[] configStrings = configStr.split("->");
        ConfigList configs = new ConfigList();

        for(String str : configStrings) {
            HashMap<String, String> options = TriggerProcessor.parseConfigString(str);
            String name = options.get("name");
            options.remove("name");

            switch (name) {
                case SecondMaxTask.Config.NAME:
                    configs.add(new SecondMaxTask.Config(options));
                    break;
                case StatsTask.Config.NAME:
                    configs.add(new StatsTask.Config(options));
            }
        }

        if(configs.isEmpty()) {
            // no valid names: just use default
            configs.add(new StatsTask.Config(new HashMap<>()));
            configs.add(new SecondMaxTask.Config(new HashMap<>()));
        }

        return configs;
    }

    @Override
    public void onMaxReached() {
        // use current weights/hotcells
        PreCalibrationService.Config cfg
                = PreCalibrationService.Config.fromPartialResult(DAQManager.getInstance().getCameraId(),
                BUILDER.buildPartial());
        CFConfig.getInstance().setPrecalConfig(cfg);
        
        if(sConfigStep < sConfigList.size()-1) {
            sConfigStep++;
            ExposureBlockManager.getInstance().newExposureBlock(CFApplication.State.PRECALIBRATION);
        } else {
            submitPrecalibrationResult();
            sConfigStep = 0;
            application.setApplicationState(CFApplication.State.CALIBRATION);
        }
    }

    /**
     * Uploads the PreCalibrationResult and saves results to SharedPreferences
     */
    private void submitPrecalibrationResult() {

        DAQManager daq = DAQManager.getInstance();

        int cameraId = daq.getCameraId();
        int resX = daq.getResX();
        int resY = daq.getResY();

        String b64Weights = Base64.encodeToString
                (BUILDER.getCompressedWeights().toByteArray(), Base64.DEFAULT);
        int[] hotcells = new int[BUILDER.getHotcellCount()];

        int i=0;
        for(int pix: BUILDER.getHotcellList()) {
            hotcells[i] = pix;
            i++;
        }

        ByteBuffer buf;
        int hotHash = -1;
        int weightHash = -1;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            Arrays.sort(hotcells);
            ByteBuffer hotBuf = ByteBuffer.allocate(hotcells.length * 4);
            hotBuf.asIntBuffer().put(hotcells);

            buf = ByteBuffer.wrap(md.digest(hotBuf.array()));
            hotHash = buf.getInt(buf.capacity() - 4) & 0x7FFFFFFF;
            CFLog.d("hot: " + hotHash);

            md.reset();

            buf = ByteBuffer.wrap(md.digest(b64Weights.getBytes("UTF-8")));
            weightHash = buf.getInt(buf.capacity() - 4) & 0x7FFFFFFF;
            CFLog.d("wgt = " + weightHash);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        PreCalibrationService.Config result
                = new PreCalibrationService.Config(cameraId, resX, resY, hotcells, hotHash, b64Weights, weightHash);

        CFConfig.getInstance().setPrecalConfig(result);

        BUILDER.setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                .setRunIdHi(application.getBuildInformation().getRunId().getMostSignificantBits())
                .setHotHash(hotHash)
                .setWgtHash(weightHash)
                .setEndTime(System.currentTimeMillis())
                .setBatteryTemp(application.getBatteryTemp())
                .setInterpolation(PreCalibrationService.INTER)
                .setPrecalConfig(sConfigList.toString());

        // submit the PreCalibrationResult object

        UploadExposureService.submitMessage(application, cameraId, BUILDER.build());

    }


    public Config getCurrentConfig() {
        return sConfigList.get(sConfigStep);
    }

    public int getStepNumber() {
        return sConfigStep;
    }

    public int getTotalSteps() {
        return sConfigList.size();
    }

}
