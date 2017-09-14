package edu.uci.crayfis.precalibration;

import android.content.Context;
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

import java.util.HashSet;
import java.util.Set;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.CFCamera;
import edu.uci.crayfis.camera.frame.RawCameraFrame;
import edu.uci.crayfis.server.UploadExposureService;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private final Context CONTEXT;
    private RenderScript RS;
    private final ScriptC_weight SCRIPT_C_WEIGHT;

    private PrecalComponent mActiveComponent;
    private final CFConfig CONFIG = CFConfig.getInstance();
    private final CFCamera CAMERA = CFCamera.getInstance();

    private final DataProtos.PreCalibrationResult.Builder PRECAL_BUILDER;

    private final long DUE_FOR_PRECAL_TIME = 7*24*3600*1000; // after 1 week, check weights and hotcells again

    private final int INTER = Imgproc.INTER_CUBIC;

    private static PreCalibrator sInstance;

    public static PreCalibrator getInstance(Context ctx) {
        if(sInstance == null) {
            sInstance = new PreCalibrator(ctx);
        }
        return sInstance;
    }

    private PreCalibrator(Context ctx) {
        CONTEXT = ctx;
        RS = CFApplication.getRenderScript();
        SCRIPT_C_WEIGHT = new ScriptC_weight(RS);
        PRECAL_BUILDER = DataProtos.PreCalibrationResult.newBuilder();
    }

    /**
     * Passes frame to the appropriate PrecalComponent
     *
     * @param frame RawCameraFrame
     * @return true if ready to switch to CALIBRATION, false otherwise
     */
    public boolean addFrame(RawCameraFrame frame) {
        if(mActiveComponent == null) {
            mActiveComponent = new HotCellKiller(RS, PRECAL_BUILDER);
        }
        if(mActiveComponent.addFrame(frame)) {
            if(mActiveComponent instanceof HotCellKiller) {
                mActiveComponent = new WeightFinder(RS, PRECAL_BUILDER);
            } else if(mActiveComponent instanceof WeightFinder) {
                submitPrecalibrationResult();
                return true;
            }
        }
        return false;
    }


    /**
     * Normalizes weights, downsamples, kills hotcells, and uploads the PreCalibrationResult
     */
    private void submitPrecalibrationResult() {

        CFApplication application = (CFApplication) CONTEXT.getApplicationContext();
        int cameraId = CAMERA.getCameraId();
        CONFIG.setLastPrecalTime(cameraId, System.currentTimeMillis());
        CONFIG.setLastPrecalResX(cameraId, CAMERA.getResX());

        PRECAL_BUILDER.setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                .setEndTime(System.currentTimeMillis())
                .setBatteryTemp(CFApplication.getBatteryTemp())
                .setInterpolation(INTER);

        // submit the PreCalibrationResult object

        UploadExposureService.submitPreCalibrationResult(CONTEXT, PRECAL_BUILDER.build());

    }


    /**
     * Creates and returns a weighting Script which includes a zero weight for hotcells.
     *
     * @param cameraId The integer ID for the camera to be weighted.  For Camera2, this is the
     *                 index of the String ID found in CameraManager.getCameraIdList()
     * @return RenderScript ScriptC with a forEach(ain, aout) method.
     */
    public ScriptC_weight getScriptCWeight(int cameraId) {

        byte[] bytes = Base64.decode(CONFIG.getPrecalWeights(cameraId), Base64.DEFAULT);

        MatOfByte compressedMat = new MatOfByte(bytes);
        Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
        Mat downsampleFloat = new Mat();
        downsampleMat.convertTo(downsampleFloat, CvType.CV_32F, 1./255);
        Mat downsampleFloat1D = downsampleFloat.reshape(0, downsampleFloat.cols() * downsampleFloat.rows());
        MatOfFloat downsampleMatOfFloat = new MatOfFloat(downsampleFloat1D);

        Mat resampledMat2D = new Mat();

        int resX = CAMERA.getResX();
        int resY = CAMERA.getResY();

        Imgproc.resize(downsampleFloat, resampledMat2D, new Size(resX, resY), 0, 0, INTER);
        Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
        MatOfFloat resampledFloat = new MatOfFloat(resampledMat);

        float[] resampledArray = resampledFloat.toArray();

        // kill hotcells in resampled frame
        Set<Integer> hotcells = CONFIG.getHotcells(cameraId);
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
        SCRIPT_C_WEIGHT.set_gWeights(weights);

        compressedMat.release();
        downsampleMat.release();
        downsampleFloat.release();
        downsampleFloat1D.release();
        downsampleMatOfFloat.release();
        resampledMat.release();
        resampledMat2D.release();
        resampledFloat.release();

        return SCRIPT_C_WEIGHT;
    }

    /**
     * Resets the Precalibration info for this camera
     */
    public void clear() {
        int cameraId = CAMERA.getCameraId();
        mActiveComponent = null;

        CONFIG.setPrecalWeights(cameraId, null);
        CONFIG.setHotcells(cameraId, new HashSet<Integer>(Camera.getNumberOfCameras()));
    }

    /**
     * Determines whether it has been sufficiently long to warrant a new PreCalibration
     *
     * @param cameraId The integer ID for the camera to be weighted.  For Camera2, this is the
     *                 index of the String ID found in CameraManager.getCameraIdList()
     * @return true if it has been at least a week since the camera in question has been precalibrated
     */
    public boolean dueForPreCalibration(int cameraId) {

        boolean expired = (System.currentTimeMillis() - CONFIG.getLastPrecalTime(cameraId)) > DUE_FOR_PRECAL_TIME;
        if(CONFIG.getPrecalWeights(cameraId) == null || CAMERA.getResX() != CONFIG.getLastPrecalResX(cameraId) || expired) {
            PRECAL_BUILDER.setStartTime(System.currentTimeMillis());
            return true;
        }
        return false;
    }
}
