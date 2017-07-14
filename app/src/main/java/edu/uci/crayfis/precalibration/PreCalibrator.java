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

    private Allocation mSumAlloc;
    private ScriptC_sumFrames mScriptCSumFrames;

    private final Context CONTEXT;
    private RenderScript RS;
    private final ScriptC_weight SCRIPT_C_WEIGHT;

    private final HotCellKiller HOTCELL_KILLER;
    private final CFConfig CONFIG = CFConfig.getInstance();


    private int mCameraId = -1;
    private int mResX;

    private int mFramesWeighting = 0;

    private long start_time;

    private final int INTER = Imgproc.INTER_CUBIC;
    private final String FORMAT = ".jpeg";

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
        HOTCELL_KILLER = new HotCellKiller(RS);
    }

    /**
     * This is the entry point of the PreCalibrator pipeline.  Sends to RenderScript to process
     * weights, or if weights have been calibrated, to HotCellKiller to find hotcell coordinates
     *
     * @param frame RawCameraFrame to process
     * @return false if done processing, true otherwise
     */
    public boolean addFrame(RawCameraFrame frame) {
        mFramesWeighting++;

        if(mFramesWeighting < CONFIG.getWeightingSampleFrames()) {
            if(mSumAlloc == null) {
                mCameraId = frame.getCameraId();
                mResX = frame.getWidth();
                Type type = new Type.Builder(RS, Element.U32(RS))
                        .setX(frame.getWidth())
                        .setY(frame.getHeight())
                        .create();

                mSumAlloc = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
                mScriptCSumFrames.set_gSum(mSumAlloc);

                start_time = System.currentTimeMillis();
            }

            mScriptCSumFrames.forEach_update(frame.getAllocation(), mSumAlloc);
        } else if(!HOTCELL_KILLER.addFrame(frame)) {
            return false;
        }

        return true;

    }

    /**
     * Normalizes weights, downsamples and resamples, kills hotcells, and uploads the PreCalibrationResult
     *
     */
    public void processPreCalResults() {

        synchronized (mSumAlloc) {

            CFApplication application = (CFApplication) CONTEXT.getApplicationContext();
            final int MAX_SAMPLE_SIZE = 1500;
            mScriptCSumFrames = null;

            int width = mSumAlloc.getType().getX();
            int height = mSumAlloc.getType().getY();
            int cameraId = mCameraId; // make sure this doesn't move from underneath us

            // first, find appropriate dimensions to downsample

            int sampleStep = getSampleStep(width, height, MAX_SAMPLE_SIZE);

            int totalFrames = CONFIG.getWeightingSampleFrames();
            ScriptC_downsample scriptCDownsample = new ScriptC_downsample(RS);

            scriptCDownsample.set_gSum(mSumAlloc);
            scriptCDownsample.set_sampleStep(sampleStep);
            scriptCDownsample.set_gTotalFrames(totalFrames);

            int sampleResX = width / sampleStep;
            int sampleResY = height / sampleStep;

            CFLog.d("Downsample resolution: " + sampleResX + "x" + sampleResY);

            // next, we downsample (average) sums in RenderScript

            Type sampleType = new Type.Builder(RS, Element.U32(RS))
                    .setX(sampleResX)
                    .setY(sampleResY)
                    .create();

            Type byteType = new Type.Builder(RS, Element.U8(RS))
                    .setX(sampleResX)
                    .setY(sampleResY)
                    .create();


            Allocation downsampledAlloc = Allocation.createTyped(RS, sampleType, Allocation.USAGE_SCRIPT);
            Allocation byteAlloc = Allocation.createTyped(RS, byteType, Allocation.USAGE_SCRIPT);

            scriptCDownsample.forEach_downsampleSums(downsampledAlloc);
            int[] downsampleArray = new int[sampleResX * sampleResY];
            downsampledAlloc.copyTo(downsampleArray);
            mSumAlloc = null;


            int minSum = 256 * totalFrames * sampleStep*sampleStep;
            for (int sum : downsampleArray) {
                if (sum < minSum) {
                    minSum = sum;
                }
            }

            scriptCDownsample.set_gMinSum(minSum + 0.5f*totalFrames*sampleStep*sampleStep);
            scriptCDownsample.forEach_normalizeWeights(downsampledAlloc, byteAlloc);

            byte[] byteNormalizedArray = new byte[sampleResX*sampleResY];
            byteAlloc.copyTo(byteNormalizedArray);

            // compress with OpenCV
            MatOfByte downsampledBytes = new MatOfByte(byteNormalizedArray);
            Mat downsampleMat2D = downsampledBytes.reshape(0, sampleResY);
            MatOfByte buf = new MatOfByte();
            int[] paramArray = new int[]{Imgcodecs.CV_IMWRITE_JPEG_QUALITY, 100};
            MatOfInt params = new MatOfInt(paramArray);
            Imgcodecs.imencode(FORMAT, downsampleMat2D, buf, params);
            byte[] bytes = buf.toArray();
            CFLog.d("Compressed bytes = " + bytes.length);
            CONFIG.setPrecalWeights(cameraId, Base64.encodeToString(bytes, Base64.DEFAULT));

            downsampledBytes.release();
            downsampleMat2D.release();
            buf.release();
            params.release();

            DataProtos.PreCalibrationResult.Builder b = DataProtos.PreCalibrationResult.newBuilder()
                    .setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                    .setStartTime(start_time)
                    .setEndTime(System.currentTimeMillis())
                    .setBatteryTemp(CFApplication.getBatteryTemp())
                    .setSampleResX(sampleResX)
                    .setSampleResY(sampleResY)
                    .setInterpolation(INTER)
                    .setCompressedWeights(ByteString.copyFrom(bytes))
                    .setCompressedFormat(FORMAT);

            Set<Integer> hotcells = HOTCELL_KILLER.HOTCELL_COORDS.get(cameraId);
            for (Integer pos: hotcells) {
                b.addHotcellX(pos % width)
                        .addHotcellY(pos / width);
            }

            int maxNonZero = HOTCELL_KILLER.secondHist.length-1;
            while(HOTCELL_KILLER.secondHist[maxNonZero] == 0) {
                maxNonZero--;
            }
            for(int i=0; i<=maxNonZero; i++) {
                b.addSecondHist(HOTCELL_KILLER.secondHist[i]);
            }

            // submit the PreCalibrationResult object

            UploadExposureService.submitPreCalibrationResult(CONTEXT, b.build());

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

    private void decompress(int cameraId, int width, int height) {

        byte[] bytes = Base64.decode(CONFIG.getPrecalWeights(cameraId), Base64.DEFAULT);

        MatOfByte compressedMat = new MatOfByte(bytes);
        Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
        Mat downsampleFloat = new Mat();
        downsampleMat.convertTo(downsampleFloat, CvType.CV_32F, 1./255);
        Mat downsampleFloat1D = downsampleFloat.reshape(0, downsampleFloat.cols() * downsampleFloat.rows());
        MatOfFloat downsampleMatOfFloat = new MatOfFloat(downsampleFloat1D);

        Mat resampledMat2D = new Mat();

        Imgproc.resize(downsampleFloat, resampledMat2D, new Size(width,height), 0, 0, INTER);
        Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
        MatOfFloat resampledFloat = new MatOfFloat(resampledMat);

        float[] resampledArray = resampledFloat.toArray();

        // kill hotcells in resampled frame
        for(Integer pos: HOTCELL_KILLER.HOTCELL_COORDS.get(cameraId)) {
            resampledArray[pos] = 0f;
        }

        Type weightType = new Type.Builder(RS, Element.F32(RS))
                .setX(width)
                .setY(height)
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

    }

    public ScriptC_weight getScriptCWeight() {
        return SCRIPT_C_WEIGHT;
    }

    public void clear(int cameraId) {
        synchronized (SCRIPT_C_WEIGHT) {
            mSumAlloc = null;
            mFramesWeighting = 0;
            HOTCELL_KILLER.clearHotcells(cameraId);
            mScriptCSumFrames = new ScriptC_sumFrames(RS);
        }
    }

    public boolean dueForPreCalibration(int cameraId) {
        Camera.Size sz = CFApplication.getCameraSize();
        if(CONFIG.getPrecalWeights(cameraId) != null && sz.width == mResX) {
            decompress(cameraId, sz.width, sz.height);
            return false;
        } else if(sz.width != mResX) {
            for(int i=0; i<Camera.getNumberOfCameras(); i++) {
                CONFIG.setPrecalWeights(cameraId, null);
            }
        }
        return true;
    }
}
