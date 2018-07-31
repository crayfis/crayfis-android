package io.crayfis.android.trigger.precalibration;

import android.util.Base64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.server.PreCalibrationService;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.TriggerProcessor;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator extends TriggerProcessor {

    public static final String KEY_HOTCELL_THRESH = "hotcell_thresh";

    private final CFCamera CAMERA;

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
    static final DataProtos.PreCalibrationResult.Builder PRECAL_BUILDER = DataProtos.PreCalibrationResult.newBuilder();

    private PreCalibrator(CFApplication app, Config config) {
        super(app, config, false);

        CAMERA = CFCamera.getInstance();
    }

    public static TriggerProcessor makeProcessor(CFApplication application) {
        if(sConfigStep == 0) {
            sConfigList = CFConfig.getInstance().getPrecalTrigger();
        }

        return new PreCalibrator(application, sConfigList.get(sConfigStep));
    }

    public static ConfigList makeConfig(String configStr) {

        String[] configStrings = configStr.split("->");
        ConfigList configs = new ConfigList();

        for(String str : configStrings) {
            HashMap<String, String> options = TriggerProcessor.parseConfigString(str);
            String name = options.get("name");
            options.remove("name");

            switch (name) {
                case HotCellTask.Config.NAME:
                    configs.add(new HotCellTask.Config(options));
                    break;
                case WeightingTask.Config.NAME:
                    configs.add(new WeightingTask.Config(options));
            }
        }

        if(configs.isEmpty()) {
            // no valid names: just use default
            configs.add(new HotCellTask.Config(new HashMap<>()));
            configs.add(new WeightingTask.Config(new HashMap<>()));
        }

        return configs;
    }

    @Override
    public void onMaxReached() {
        if(sConfigStep < sConfigList.size()-1) {
            sConfigStep++;
            ExposureBlockManager.getInstance().newExposureBlock(CFApplication.State.PRECALIBRATION);
        } else {
            submitPrecalibrationResult();
            sConfigStep = 0;
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
    }

    /**
     * Uploads the PreCalibrationResult and saves results to SharedPreferences
     */
    private void submitPrecalibrationResult() {

        int cameraId = CAMERA.getCameraId();
        int resX = CAMERA.getResX();
        int resY = CAMERA.getResY();
        UUID precalId = UUID.randomUUID();

        String b64Weights = Base64.encodeToString
                (PRECAL_BUILDER.getCompressedWeights().toByteArray(), Base64.DEFAULT);
        int[] hotcells = new int[PRECAL_BUILDER.getHotcellCount()];
        String precalIdStr = precalId.toString().replace("-", "");

        int i=0;
        for(int pix: PRECAL_BUILDER.getHotcellList()) {
            hotcells[i] = pix;
            i++;
        }

        PreCalibrationService.Config result
                = new PreCalibrationService.Config(cameraId, resX, resY, b64Weights, hotcells, precalIdStr);

        CFConfig.getInstance().setPrecalConfig(result);

        PRECAL_BUILDER.setRunId(mApplication.getBuildInformation().getRunId().getLeastSignificantBits())
                .setRunIdHi(mApplication.getBuildInformation().getRunId().getMostSignificantBits())
                .setPrecalId(precalId.getLeastSignificantBits())
                .setPrecalIdHi(precalId.getMostSignificantBits())
                .setEndTime(System.currentTimeMillis())
                .setBatteryTemp(mApplication.getBatteryTemp())
                .setInterpolation(PreCalibrationService.INTER)
                .setPrecalConfig(sConfigList.toString());

        // submit the PreCalibrationResult object

        UploadExposureService.submitMessage(mApplication, cameraId, PRECAL_BUILDER.build());

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
