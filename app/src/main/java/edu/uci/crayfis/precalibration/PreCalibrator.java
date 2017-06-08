package edu.uci.crayfis.precalibration;

import android.content.Context;
import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.math.BigInteger;

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

    private RenderScript mRS;
    private ScriptC_weight mScriptCWeight;
    private ScriptC_sumFrames mScriptCSumFrames;

    private final float[][] mDownsampledWeights = new float[Camera.getNumberOfCameras()][];
    private Allocation mWeights;
    private final HotCellKiller HOTCELL_KILLER = new HotCellKiller();
    private int mCameraId = -1;

    private int mFramesWeighting = 0;
    private Boolean mAlreadySent = false;

    private long start_time;

    private static PreCalibrator sInstance = null;

    public static PreCalibrator getInstance() {
        if(sInstance == null) {
            sInstance = new PreCalibrator();
        }
        return sInstance;
    }

    private PreCalibrator() { }

    /**
     * This is the entry point of the PreCalibrator pipeline.  Sends to RenderScript to process
     * weights, or if weights have been calibrated, to HotCellKiller to find hotcell coordinates
     *
     * @param frame RawCameraFrame to process
     * @return false if done processing, true otherwise
     */
    public boolean addFrame(RawCameraFrame frame) {
        mFramesWeighting++;

        if(mWeights == null || frame.getCameraId() != mCameraId
                || mWeights.getType().getX() != frame.getWidth()) {

            mCameraId = frame.getCameraId();
            Type type = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(frame.getWidth())
                    .setY(frame.getHeight())
                    .create();

            mWeights = Allocation.createTyped(mRS, type, Allocation.USAGE_SCRIPT);
            mScriptCSumFrames.set_gSum(mWeights);

            start_time = System.currentTimeMillis();
        }

        if(mFramesWeighting < CFConfig.getInstance().getWeightingSampleFrames()) {
            mScriptCSumFrames.forEach_update(frame.getAllocation(), mWeights);
        } else if(!HOTCELL_KILLER.addFrame(frame)) {
            synchronized (mAlreadySent) {
                if(!mAlreadySent) {
                    mAlreadySent = true;
                    return false;
                }
            }
        }

        return true;

    }

    /**
     * Normalizes weights, downsamples and resamples, kills hotcells, and uploads the PreCalibrationResult
     *
     * @param context Application context
     */
    public void processPreCalResults(Context context) {

        synchronized (mDownsampledWeights) {

            CFApplication application = (CFApplication) context.getApplicationContext();
            final int maxSampleSize = 1500;
            //ArrayList<HotCellKiller.Hotcell> hotcellCoords = HOTCELL_KILLER.getHotcellCoords();
            mScriptCSumFrames = null;

            int width = mWeights.getType().getX();
            int height = mWeights.getType().getY();
            int cameraId = mCameraId; // make sure this doesn't move from underneath us

            // first, find appropriate dimensions to downsample

            int sampleStep = getSampleStep(width, height, maxSampleSize);

            int totalFrames = CFConfig.getInstance().getWeightingSampleFrames();
            ScriptC_downsample scriptCDownsample = new ScriptC_downsample(mRS);

            scriptCDownsample.set_gWeights(mWeights);
            scriptCDownsample.set_gMinSum(256 * totalFrames);
            scriptCDownsample.set_sampleStep(sampleStep);
            scriptCDownsample.set_gTotalFrames(totalFrames);

            int sampleResX = width / sampleStep;
            int sampleResY = height / sampleStep;

            CFLog.d("Downsample resolution: " + sampleResX + "x" + sampleResY);

            // next, we downsample (average) sums in RenderScript

            Type sampleType = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(sampleResX)
                    .setY(sampleResY)
                    .create();

            Allocation downsampledAlloc = Allocation.createTyped(mRS, sampleType, Allocation.USAGE_SCRIPT);

            scriptCDownsample.forEach_downsampleWeights(downsampledAlloc);
            float[] downsampleArray = new float[sampleResX * sampleResY];
            downsampledAlloc.copyTo(downsampleArray);

            float minSum = 256*totalFrames;
            for(float sum: downsampleArray) {
                if(sum < minSum) {
                    minSum = sum;
                }
            }

            scriptCDownsample.set_gMinSum(minSum + 0.5f * totalFrames);
            scriptCDownsample.forEach_normalizeWeights(downsampledAlloc, downsampledAlloc);

            downsampledAlloc.copyTo(downsampleArray);
            mDownsampledWeights[cameraId] = downsampleArray;

            // finally, resample in OpenCV

            resample(cameraId, sampleStep, sampleResY);

            DataProtos.PreCalibrationResult.Builder b = DataProtos.PreCalibrationResult.newBuilder()
                    .setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                    .setStartTime(start_time)
                    .setEndTime(System.currentTimeMillis())
                    .setSampleResX(sampleResX)
                    .setSampleResY(sampleResY);

            for (int iy = 0; iy < sampleResY; iy++) {
                for (int ix = 0; ix < sampleResX; ix++) {
                    b.addWeights(mDownsampledWeights[cameraId][ix + sampleResX * iy]);
                }
            }

            // submit the PreCalibrationResult object

            UploadExposureService.submitPreCalibrationResult(context, b.build());

        }
    }

    private int getSampleStep(int w, int h, int maxArea) {
        // dimensions of each "block" that make up aspect ratio
        int blockSize = BigInteger.valueOf(h).gcd(BigInteger.valueOf(w)).intValue();

        int sampleStep = (int) Math.sqrt((w * h) / maxArea);

        while (blockSize % sampleStep != 0) {
            sampleStep++;
            if (sampleStep > blockSize) {
                return -1;
            }
        }

        return sampleStep;
    }

    private void resample(int cameraId, int scale, int downsampleRows) {

        MatOfFloat downsampleMat = new MatOfFloat(mDownsampledWeights[cameraId]);
        Mat downsampleMat2D = downsampleMat.reshape(0, downsampleRows);
        Mat resampledMat2D = new Mat();

        Imgproc.resize(downsampleMat2D, resampledMat2D, new Size(), scale, scale, Imgproc.INTER_CUBIC);
        Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
        MatOfFloat resampledFloat = new MatOfFloat(resampledMat);

        float[] resampledArray = resampledFloat.toArray();
        int width = resampledMat2D.rows();
        CFLog.d("width = " + width);

        // kill hotcells in resampled frame
        for(HotCellKiller.Hotcell c: HOTCELL_KILLER.getHotcellCoords()) {
            resampledArray[c.x + width*c.y] = 0f;
        }

        mWeights.copyFrom(resampledFloat.toArray());
        mScriptCWeight.set_gWeights(mWeights);

        downsampleMat.release();
        resampledMat.release();
        downsampleMat2D.release();
        resampledMat2D.release();
        resampledFloat.release();

    }

    public ScriptC_weight getScriptCWeight(RenderScript rs) {
        if(mScriptCWeight == null) {
            mRS = rs;
            mScriptCWeight = new ScriptC_weight(rs);
        }
        return mScriptCWeight;
    }

    public void clear(int cameraId) {
        synchronized (mDownsampledWeights) {
            mDownsampledWeights[cameraId] = null;
            mWeights = null;
            mFramesWeighting = 0;
            HOTCELL_KILLER.clearHotcells();
            mScriptCSumFrames = new ScriptC_sumFrames(mRS);
            mAlreadySent = false;
        }
    }

    public boolean dueForPreCalibration(int cameraId) {
        return mDownsampledWeights[cameraId] == null
                || CFApplication.getCameraSize().width != mWeights.getType().getX();
    }

}
