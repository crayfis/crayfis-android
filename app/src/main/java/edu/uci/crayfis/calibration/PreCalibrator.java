package edu.uci.crayfis.calibration;

import android.content.Context;
import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float2;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.CFCamera;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private RenderScript mRS;
    private ScriptC_weight mScriptCWeight;
    private Allocation[] mWeights = new Allocation[Camera.getNumberOfCameras()];

    private long start_time;

    private static PreCalibrator sInstance = null;

    public static PreCalibrator getInstance() {
        if(sInstance == null) {
            sInstance = new PreCalibrator();
        }
        return sInstance;
    }

    private PreCalibrator() { }

    public void addFrame(RawCameraFrame frame) {
        frame.getAllocation();
        int cameraId = frame.getCameraId();

        if(mWeights[cameraId] == null
                || mWeights[cameraId].getType().getX() != frame.getWidth()) {

            Type type = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(frame.getWidth())
                    .setY(frame.getHeight())
                    .create();

            mWeights[frame.getCameraId()] = Allocation.createTyped(mRS, type, Allocation.USAGE_SCRIPT);
            mScriptCWeight.set_gWeights(mWeights[cameraId]);

            start_time = System.currentTimeMillis();
        }

        mScriptCWeight.forEach_update(frame.getAllocation(), mWeights[cameraId]);

    }

    public void processPreCalResults(Context context) {

        synchronized (mWeights) {

            // first, find appropriate dimensions to downsample

            CFApplication application = (CFApplication) context.getApplicationContext();
            int cameraId = application.getCameraId();
            final int maxSampleSize = 1500;

            int width = mWeights[cameraId].getType().getX();
            int height = mWeights[cameraId].getType().getY();

            // dimensions of each "block" that make up aspect ratio
            int blockSize = BigInteger.valueOf(height).gcd(BigInteger.valueOf(width)).intValue();

            int sampleStep = (int) Math.sqrt((width * height) / maxSampleSize);

            mScriptCWeight.set_gMinSum(256 * mScriptCWeight.get_gTotalFrames());
            mScriptCWeight.set_sampleStep(sampleStep);

            while (blockSize % sampleStep != 0) {
                sampleStep++;
                if (sampleStep > blockSize) {
                    return;
                }
            }

            int sampleResX = width / sampleStep;
            int sampleResY = height / sampleStep;

            CFLog.d("Downsample resolution: " + sampleResX + "x" + sampleResY);

            // next, downsample in RenderScript

            Type sampleType = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(sampleResX)
                    .setY(sampleResY)
                    .create();

            Allocation downsample = Allocation.createTyped(mRS, sampleType, Allocation.USAGE_SCRIPT);

            mScriptCWeight.forEach_downsampleWeights(downsample);

            mScriptCWeight.forEach_findMin(downsample);
            mScriptCWeight.forEach_normalizeWeights(downsample, downsample);

            // finally, resample in OpenCV

            float[] downsampleArray = new float[sampleResX * sampleResY];
            downsample.copyTo(downsampleArray);

            MatOfFloat downsampleMat = new MatOfFloat(downsampleArray);
            Mat downsampleMat2D = downsampleMat.reshape(0, sampleResY);
            Mat resampledMat2D = new Mat();

            Imgproc.resize(downsampleMat2D, resampledMat2D, new Size(), sampleStep, sampleStep, Imgproc.INTER_CUBIC);
            Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
            MatOfFloat resampledFloat = new MatOfFloat(resampledMat);

            mWeights[cameraId].copyFromUnchecked(resampledFloat.toArray());

            downsampleMat.release();
            resampledMat.release();
            downsampleMat2D.release();
            resampledMat2D.release();
            resampledFloat.release();


            // submit the PreCalibrationResult object

            DataProtos.PreCalibrationResult.Builder b = DataProtos.PreCalibrationResult.newBuilder()
                    .setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                    .setStartTime(start_time)
                    .setEndTime(System.currentTimeMillis())
                    .setSampleResX(sampleResX)
                    .setSampleResY(sampleResY);

            for (int iy = 0; iy < sampleResY; iy++) {
                for (int ix = 0; ix < sampleResX; ix++) {
                    b.addWeights(downsampleArray[ix + sampleResX * iy]);
                }
            }


            UploadExposureService.submitPreCalibrationResult(context, b.build());
        }
    }


    public ScriptC_weight getScriptCWeight(RenderScript rs) {
        if(mScriptCWeight == null) {
            mRS = rs;
            mScriptCWeight = new ScriptC_weight(rs);
            mScriptCWeight.set_gTotalFrames(CFConfig.getInstance().getPreCalibrationSampleFrames());
        }
        return mScriptCWeight;
    }

    public void clear(int cameraId) {
        synchronized (mWeights) {
            mWeights[cameraId] = null;
        }
    }

    public boolean dueForPreCalibration(int cameraId) {
        return mWeights[cameraId] == null
                || CFApplication.getCameraSize().width != mWeights[cameraId].getType().getX();
    }

}
