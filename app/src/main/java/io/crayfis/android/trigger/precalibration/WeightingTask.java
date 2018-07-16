package io.crayfis.android.trigger.precalibration;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import com.google.protobuf.ByteString;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

import java.math.BigInteger;
import java.util.HashMap;

import io.crayfis.android.server.CFConfig;
import io.crayfis.android.ScriptC_downsample;
import io.crayfis.android.ScriptC_sumFrames;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 7/18/2017.
 */

class WeightingTask extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "weight";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(KEY_MAXFRAMES, 1000);
        }

        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            //FIXME: what to do with this?
            return null;
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new WeightingTask(processor, this);
        }
    }

    private Allocation mSumAlloc;
    private ScriptC_sumFrames mScriptCSumFrames;
    private final RenderScript RS;
    private final Config mConfig;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private final CFCamera CAMERA = CFCamera.getInstance();
    private static final String FORMAT = ".jpeg";

    WeightingTask(TriggerProcessor processor, Config config) {
        super(processor);

        mConfig = config;
        RS = processor.mApplication.getRenderScript();

        CFLog.i("WeightingTask created");
        mScriptCSumFrames = new ScriptC_sumFrames(RS);

        Type type = new Type.Builder(RS, Element.I32(RS))
                .setX(CAMERA.getResX())
                .setY(CAMERA.getResY())
                .create();

        mSumAlloc = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        mScriptCSumFrames.set_gSum(mSumAlloc);
    }

    /**
     * Performs a running element-wise addition for each pixel in the frame
     *
     * @param frame RawCameraFrame
     * @return true if we have reached the requisite number of frames, false otherwise
     */
    @Override
    protected int processFrame(RawCameraFrame frame) {
        mScriptCSumFrames.forEach_update(frame.getWeightedAllocation());
        return 1;
    }

    /**
     * Calculates weights based on running sum, then compresses and stores them in a protobuf file
     * and in the SharedPreferences
     */
    @Override
    protected void onMaxReached() {

        final int MAX_SAMPLE_SIZE = 1500;

        int cameraId = CAMERA.getCameraId();
        int width = mSumAlloc.getType().getX();
        int height = mSumAlloc.getType().getY();

        // first, find appropriate dimensions to downsample

        int sampleStep = getSampleStep(width, height, MAX_SAMPLE_SIZE);

        int totalFrames = mConfig.getInt(TriggerProcessor.Config.KEY_MAXFRAMES);
        ScriptC_downsample scriptCDownsample = new ScriptC_downsample(RS);

        scriptCDownsample.set_gSum(mSumAlloc);
        scriptCDownsample.set_sampleStep(sampleStep);
        scriptCDownsample.set_gTotalFrames(totalFrames);

        for(int pos: PreCalibrator.PRECAL_BUILDER.getHotcellList()) {
            int x = pos % width;
            int y = pos / width;
            scriptCDownsample.invoke_killHotcell(x, y);
        }

        int sampleResX = width / sampleStep;
        int sampleResY = height / sampleStep;

        CFLog.d("Downsample resolution: " + sampleResX + "x" + sampleResY);

        // next, we downsample (average) sums in RenderScript

        Type sampleType = new Type.Builder(RS, Element.F32(RS))
                .setX(sampleResX)
                .setY(sampleResY)
                .create();

        Type byteType = new Type.Builder(RS, Element.U8(RS))
                .setX(sampleResX)
                .setY(sampleResY)
                .create();


        Allocation downsampledAlloc = Allocation.createTyped(RS, sampleType, Allocation.USAGE_SCRIPT);
        Allocation byteAlloc = Allocation.createTyped(RS, byteType, Allocation.USAGE_SCRIPT);

        // we find the block with the smallest average pix_val to normalize
        scriptCDownsample.forEach_downsampleSums(downsampledAlloc);
        float[] downsampleArray = new float[sampleResX * sampleResY];
        downsampledAlloc.copyTo(downsampleArray);

        float minAvg = 256f;
        for (float avg : downsampleArray) {
            if (avg < minAvg) {
                minAvg = avg;
            }
        }
        CFLog.d("minSum = " + minAvg*sampleStep*sampleStep*totalFrames);

        // we use log(1 + 1/mu) as our weights, which takes into account the nonlinearity of
        // truncation on the sums, assuming exponentially distributed noise for simplicity
        double maxWeight = Math.log1p(1f/minAvg);

        CFLog.d("maxWeight = " + maxWeight);

        scriptCDownsample.set_gMaxWeight((float)maxWeight);
        scriptCDownsample.forEach_normalizeWeights(downsampledAlloc, byteAlloc);

        byte[] byteNormalizedArray = new byte[sampleResX * sampleResY];
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

        downsampledBytes.release();
        downsampleMat2D.release();
        buf.release();
        params.release();

        // now store weights in PreCalibrationResult
        PreCalibrator.PRECAL_BUILDER.setSampleResX(sampleResX)
                .setSampleResY(sampleResY)
                .setCompressedWeights(ByteString.copyFrom(bytes))
                .setCompressedFormat(FORMAT)
                .setResX(width);
    }

    /**
     * Find the appropriate factor to scale down by evenly, given a maximum area after downsample
     *
     * @param w Initial x resolution
     * @param h Initial y resolution
     * @param maxArea Maximum number of cells allowed after downsample
     * @return
     */
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
}
