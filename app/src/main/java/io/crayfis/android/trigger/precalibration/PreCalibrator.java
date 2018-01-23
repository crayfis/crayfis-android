package io.crayfis.android.trigger.precalibration;

import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Base64;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.DataProtos;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.server.UploadExposureService;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator extends TriggerProcessor {

    static final String KEY_HOTCELL_THRESH = "hotcell_thresh";

    private final CFConfig CONFIG;
    private final CFCamera CAMERA;

    public static class ConfigList extends ArrayList<Config> {

        @Override
        public String toString() {
            if(size() == 0) return "";
            StringBuilder sb = new StringBuilder();
            Config firstConfig = get(0);
            sb.append(firstConfig);
            remove(0);
            for(Config config : this) {
                sb.append(" -> ")
                        .append(config);
            }
            return sb.toString();
        }
    }

    private static List<Config> sConfigList;
    private static int sConfigStep = 0;
    static final DataProtos.PreCalibrationResult.Builder PRECAL_BUILDER = DataProtos.PreCalibrationResult.newBuilder();

    private final static int INTER = Imgproc.INTER_CUBIC;

    private PreCalibrator(CFApplication app, Config config) {
        super(app, config, false);

        CONFIG = CFConfig.getInstance();
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
            configs.add(new HotCellTask.Config(new HashMap<String, String>()));
            configs.add(new WeightingTask.Config(new HashMap<String, String>()));
        }

        return configs;
    }

    @Override
    public void onMaxReached() {
        if(sConfigStep < sConfigList.size()-1) {
            sConfigStep++;
            ExposureBlockManager.getInstance(mApplication).newExposureBlock(CFApplication.State.PRECALIBRATION);
        } else {
            submitPrecalibrationResult();
            sConfigStep = 0;
            mApplication.setApplicationState(CFApplication.State.CALIBRATION);
        }
    }

    /**
     * Normalizes weights, downsamples, kills hotcells, and uploads the PreCalibrationResult
     */
    private void submitPrecalibrationResult() {

        int cameraId = CAMERA.getCameraId();
        CONFIG.setLastPrecalTime(cameraId, System.currentTimeMillis());
        CONFIG.setLastPrecalResX(cameraId, CAMERA.getResX());
        CONFIG.setPrecalId(cameraId, UUID.randomUUID());

        PRECAL_BUILDER.setRunId(mApplication.getBuildInformation().getRunId().getLeastSignificantBits())
                .setRunIdHi(mApplication.getBuildInformation().getRunId().getMostSignificantBits())
                .setPrecalId(CONFIG.getPrecalId(cameraId).getLeastSignificantBits())
                .setPrecalIdHi(CONFIG.getPrecalId(cameraId).getMostSignificantBits())
                .setEndTime(System.currentTimeMillis())
                .setBatteryTemp(mApplication.getBatteryTemp())
                .setInterpolation(INTER);

        // submit the PreCalibrationResult object

        UploadExposureService.submitPreCalibrationResult(mApplication, PRECAL_BUILDER.build());

    }


    /**
     * Creates and returns a weighting Script which includes a zero weight for hotcells.
     *
     * @param cameraId The integer ID for the camera to be weighted.  For Camera2, this is the
     *                 index of the String ID found in CameraManager.getCameraIdList()
     * @return RenderScript ScriptC with a forEach(ain, aout) method.
     */
    public static void updateWeights(RenderScript RS, int cameraId) {

        CFConfig config = CFConfig.getInstance();
        CFCamera camera = CFCamera.getInstance();

        byte[] bytes = Base64.decode(config.getPrecalWeights(cameraId), Base64.DEFAULT);

        MatOfByte compressedMat = new MatOfByte(bytes);
        Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
        Mat downsampleFloat = new Mat();
        downsampleMat.convertTo(downsampleFloat, CvType.CV_32F, 1./255);
        Mat downsampleFloat1D = downsampleFloat.reshape(0, downsampleFloat.cols() * downsampleFloat.rows());
        MatOfFloat downsampleMatOfFloat = new MatOfFloat(downsampleFloat1D);

        Mat resampledMat2D = new Mat();

        int resX = camera.getResX();
        int resY = camera.getResY();

        Imgproc.resize(downsampleFloat, resampledMat2D, new Size(resX, resY), 0, 0, INTER);
        Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
        MatOfFloat resampledFloat = new MatOfFloat(resampledMat);

        float[] resampledArray = resampledFloat.toArray();

        // kill hotcells in resampled frame
        Set<Integer> hotcells = config.getHotcells(cameraId);
        if(hotcells != null) {
            for (Integer pos : hotcells) {
                resampledArray[pos] = 0f;
            }
        }

        Type weightType = new Type.Builder(RS, Element.F32(RS))
                .setX(resX)
                .setY(resY)
                .create();
        Allocation weights = Allocation.createTyped(RS, weightType, Allocation.USAGE_SCRIPT);
        weights.copyFrom(resampledArray);
        ScriptC_weight scriptCWeight = new ScriptC_weight(RS);
        scriptCWeight.set_gWeights(weights);
        CFCamera.getInstance().getFrameBuilder().setWeights(scriptCWeight);

        compressedMat.release();
        downsampleMat.release();
        downsampleFloat.release();
        downsampleFloat1D.release();
        downsampleMatOfFloat.release();
        resampledMat.release();
        resampledMat2D.release();
        resampledFloat.release();
    }


    /**
     * Determines whether it has been sufficiently long to warrant a new PreCalibration
     *
     * @param cameraId The integer ID for the camera to be weighted.  For Camera2, this is the
     *                 index of the String ID found in CameraManager.getCameraIdList()
     * @return true if it has been at least a week since the camera in question has been precalibrated
     */
    public static boolean dueForPreCalibration(int cameraId) {

        CFConfig config = CFConfig.getInstance();
        CFCamera camera = CFCamera.getInstance();

        if(config.getPrecalResetTime() == null) return false;
        boolean expired = (System.currentTimeMillis() - config.getLastPrecalTime(cameraId)) > config.getPrecalResetTime();
        boolean hasWeights = config.getPrecalWeights(cameraId) == null;
        boolean sameRes = camera.getResX() == config.getLastPrecalResX(cameraId);

        if(expired || hasWeights || !sameRes) {
            // clear everything in preparation
            sConfigStep = 0;
            sConfigList = null;
            PRECAL_BUILDER.clear();
            CFConfig.getInstance().setPrecalWeights(cameraId, null);
            CFConfig.getInstance().setHotcells(cameraId, new HashSet<Integer>(Camera.getNumberOfCameras()));
            return true;
        }

        return false;
    }

    public static TriggerProcessor.Config getCurrentConfig() {
        return sConfigList.get(sConfigStep);
    }

}
