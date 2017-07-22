package edu.uci.crayfis.precalibration;

import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Base64;

import com.google.protobuf.ByteString;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

import java.math.BigInteger;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_downsample;
import edu.uci.crayfis.ScriptC_sumFrames;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 7/18/2017.
 */

public class WeightFinder extends PrecalComponent {

    private Allocation mSumAlloc;
    private ScriptC_sumFrames mScriptCSumFrames;

    private final CFConfig CONFIG = CFConfig.getInstance();
    final String FORMAT = ".jpeg";

    WeightFinder(RenderScript rs, DataProtos.PreCalibrationResult.Builder b) {
        super(rs, b);
        CFLog.i("WeightFinder created");
        mScriptCSumFrames = new ScriptC_sumFrames(RS);
        sampleFrames = CONFIG.getWeightingSampleFrames();

        Camera.Size sz = CFApplication.getCameraSize();
        Type type = new Type.Builder(RS, Element.U32(RS))
                .setX(sz.width)
                .setY(sz.height)
                .create();

        mSumAlloc = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        mScriptCSumFrames.set_gSum(mSumAlloc);
    }

    @Override
    boolean addFrame(RawCameraFrame frame) {
        mScriptCSumFrames.forEach_update(frame.getAllocation(), mSumAlloc);
        return super.addFrame(frame);
    }

    @Override
    void process() {

        final int MAX_SAMPLE_SIZE = 1500;

        int cameraId = CFApplication.getCameraId();
        int width = mSumAlloc.getType().getX();
        int height = mSumAlloc.getType().getY();

        // first, find appropriate dimensions to downsample

        int sampleStep = getSampleStep(width, height, MAX_SAMPLE_SIZE);

        int totalFrames = CONFIG.getWeightingSampleFrames();
        ScriptC_downsample scriptCDownsample = new ScriptC_downsample(RS);

        scriptCDownsample.set_gSum(mSumAlloc);
        scriptCDownsample.set_sampleStep(sampleStep);
        scriptCDownsample.set_gPixPerSample((float)totalFrames*sampleStep*sampleStep);

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

        int minSum = 256 * totalFrames * sampleStep * sampleStep;
        for (int sum : downsampleArray) {
            if (sum < minSum) {
                minSum = sum;
            }
        }
        CFLog.d("minSum = " + minSum);

        double maxWeight = Math.log1p((float)totalFrames*sampleStep*sampleStep/++minSum);

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
        CONFIG.setPrecalWeights(cameraId, Base64.encodeToString(bytes, Base64.DEFAULT));

        downsampledBytes.release();
        downsampleMat2D.release();
        buf.release();
        params.release();

        BUILDER.setSampleResX(sampleResX)
                .setSampleResY(sampleResY)
                .setCompressedWeights(ByteString.copyFrom(bytes))
                .setCompressedFormat(FORMAT)
                .setResX(width);
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
}
