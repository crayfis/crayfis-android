package edu.uci.crayfis.precalibration;

import android.content.Context;
import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Base64;

import com.google.protobuf.ByteString;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_downsample;
import edu.uci.crayfis.ScriptC_sumFrames;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private final Context CONTEXT;
    private RenderScript RS;
    private final ScriptC_weight SCRIPT_C_WEIGHT;

    public final WeightFinder WEIGHT_FINDER;
    public final HotCellKiller HOTCELL_KILLER;
    private final CFConfig CONFIG = CFConfig.getInstance();

    private final DataProtos.PreCalibrationResult.Builder BUILDER;

    private int mResX;

    private final int INTER = Imgproc.INTER_CUBIC;

    private static PreCalibrator sInstance = null;

    public static PreCalibrator getInstance(Context ctx) {
        if(sInstance == null) {
            sInstance = new PreCalibrator(ctx);
        }
        return sInstance;
    }

    private PreCalibrator(Context ctx) {
        CONTEXT = ctx;
        RS = RenderScript.create(ctx);
        SCRIPT_C_WEIGHT = new ScriptC_weight(RS);
        BUILDER = DataProtos.PreCalibrationResult.newBuilder();

        WEIGHT_FINDER = new WeightFinder(RS, BUILDER);
        HOTCELL_KILLER = new HotCellKiller(RS, BUILDER);
    }


    /**
     * Normalizes weights, downsamples, kills hotcells, and uploads the PreCalibrationResult
     */
    public void submitPrecalibrationResult() {

            CFApplication application = (CFApplication) CONTEXT.getApplicationContext();

            BUILDER.setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                    .setEndTime(System.currentTimeMillis())
                    .setBatteryTemp(CFApplication.getBatteryTemp())
                    .setInterpolation(INTER);

            // submit the PreCalibrationResult object

            UploadExposureService.submitPreCalibrationResult(CONTEXT, BUILDER.build());

    }


    public ScriptC_weight getScriptCWeight(int cameraId) {

        Camera.Size sz = CFApplication.getCameraSize();
        mResX = sz.width;

        byte[] bytes = Base64.decode(CONFIG.getPrecalWeights(cameraId), Base64.DEFAULT);

        MatOfByte compressedMat = new MatOfByte(bytes);
        Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
        Mat downsampleFloat = new Mat();
        downsampleMat.convertTo(downsampleFloat, CvType.CV_32F, 1./255);
        Mat downsampleFloat1D = downsampleFloat.reshape(0, downsampleFloat.cols() * downsampleFloat.rows());
        MatOfFloat downsampleMatOfFloat = new MatOfFloat(downsampleFloat1D);

        Mat resampledMat2D = new Mat();

        Imgproc.resize(downsampleFloat, resampledMat2D, new Size(sz.width, sz.height), 0, 0, INTER);
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
                .setX(sz.width)
                .setY(sz.height)
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

    public void clear() {
        synchronized (SCRIPT_C_WEIGHT) {
            WEIGHT_FINDER.clear();
            HOTCELL_KILLER.clear();
            BUILDER.setStartTime(System.currentTimeMillis());
        }
    }

    public boolean dueForPreCalibration(int cameraId) {
        Camera.Size sz = CFApplication.getCameraSize();
        return CONFIG.getPrecalWeights(cameraId) == null || sz.width != mResX;
    }
}
