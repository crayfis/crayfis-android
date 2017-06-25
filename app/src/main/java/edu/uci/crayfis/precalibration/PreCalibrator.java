package edu.uci.crayfis.precalibration;

import android.content.Context;
import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

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

    private final byte[][] mCompressedWeights = new byte[Camera.getNumberOfCameras()][];
    private Allocation mSumAlloc;
    private final HotCellKiller HOTCELL_KILLER = new HotCellKiller();
    private int mCameraId = -1;
    private int mResX;

    private int mFramesWeighting = 0;
    private Boolean mAlreadySent = false;

    private long start_time;

    private final int INTER = Imgproc.INTER_CUBIC;
    private final String FORMAT = ".jpeg";

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

        if(mFramesWeighting < CFConfig.getInstance().getWeightingSampleFrames()) {
            if(mSumAlloc == null) {
                mCameraId = frame.getCameraId();
                mResX = frame.getWidth();
                Type type = new Type.Builder(mRS, Element.U32(mRS))
                        .setX(frame.getWidth())
                        .setY(frame.getHeight())
                        .create();

                mSumAlloc = Allocation.createTyped(mRS, type, Allocation.USAGE_SCRIPT);
                mScriptCSumFrames.set_gSum(mSumAlloc);

                start_time = System.currentTimeMillis();
            }

            mScriptCSumFrames.forEach_update(frame.getAllocation(), mSumAlloc);
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

        synchronized (mSumAlloc) {

            CFApplication application = (CFApplication) context.getApplicationContext();
            final int maxSampleSize = 1500;
            mScriptCSumFrames = null;

            int width = mSumAlloc.getType().getX();
            int height = mSumAlloc.getType().getY();
            int cameraId = mCameraId; // make sure this doesn't move from underneath us

            // first, find appropriate dimensions to downsample

            int sampleStep = getSampleStep(width, height, maxSampleSize);

            int totalFrames = CFConfig.getInstance().getWeightingSampleFrames();
            ScriptC_downsample scriptCDownsample = new ScriptC_downsample(mRS);

            scriptCDownsample.set_gSum(mSumAlloc);
            scriptCDownsample.set_sampleStep(sampleStep);
            scriptCDownsample.set_gTotalFrames(totalFrames);

            int sampleResX = width / sampleStep;
            int sampleResY = height / sampleStep;

            CFLog.d("Downsample resolution: " + sampleResX + "x" + sampleResY);

            // next, we downsample (average) sums in RenderScript

            Type sampleType = new Type.Builder(mRS, Element.U32(mRS))
                    .setX(sampleResX)
                    .setY(sampleResY)
                    .create();

            Type byteType = new Type.Builder(mRS, Element.U8(mRS))
                    .setX(sampleResX)
                    .setY(sampleResY)
                    .create();


            Allocation downsampledAlloc = Allocation.createTyped(mRS, sampleType, Allocation.USAGE_SCRIPT);
            Allocation byteAlloc = Allocation.createTyped(mRS, byteType, Allocation.USAGE_SCRIPT);

            scriptCDownsample.forEach_downsampleSums(downsampledAlloc);
            int[] downsampleArray = new int[sampleResX * sampleResY];
            downsampledAlloc.copyTo(downsampleArray);
            mSumAlloc = null;


            float minSum = 256 * totalFrames * sampleStep*sampleStep;
            for (float sum : downsampleArray) {
                CFLog.d("sum = " + sum);
                if (sum < minSum) {
                    minSum = sum;
                }
            }

            scriptCDownsample.set_gMinSum(minSum + 0.5f * totalFrames);
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
            mCompressedWeights[cameraId] = buf.toArray();
            CFLog.d("Compressed bytes = " + mCompressedWeights.length);

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
                    .setCompressedWeights(ByteString.copyFrom(mCompressedWeights[cameraId]))
                    .setCompressedFormat(FORMAT);

            ArrayList<HotCellKiller.Hotcell> hotcells = HOTCELL_KILLER.getHotcellCoords(cameraId);
            for (int i = 0; i < hotcells.size(); i++) {
                b.addHotcellX(hotcells.get(i).x)
                        .addHotcellY(hotcells.get(i).y);
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

    private void decompress(int cameraId, int width, int height) {

        MatOfByte compressedMat = new MatOfByte(mCompressedWeights[cameraId]);
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
        for(HotCellKiller.Hotcell c: HOTCELL_KILLER.getHotcellCoords(cameraId)) {
            resampledArray[c.x + width*c.y] = 0f;
        }

        Type weightType = new Type.Builder(mRS, Element.F32(mRS))
                .setX(width)
                .setY(height)
                .create();
        Allocation weights = Allocation.createTyped(mRS, weightType, Allocation.USAGE_SCRIPT);
        weights.copyFrom(resampledArray);
        mScriptCWeight.set_gWeights(weights);

        compressedMat.release();
        downsampleMat.release();
        downsampleFloat.release();
        downsampleFloat1D.release();
        downsampleMatOfFloat.release();
        resampledMat.release();
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
        synchronized (mCompressedWeights) {
            mCompressedWeights[cameraId] = null;
            mSumAlloc = null;
            mFramesWeighting = 0;
            HOTCELL_KILLER.clearHotcells(cameraId);
            mScriptCSumFrames = new ScriptC_sumFrames(mRS);
            mAlreadySent = false;
        }
    }

    public boolean dueForPreCalibration(int cameraId) {
        Camera.Size sz = CFApplication.getCameraSize();
        if(mCompressedWeights[cameraId] != null && sz.width == mResX) {
            decompress(cameraId, sz.width, sz.height);
            return false;
        } else if(sz.width != mResX) {
            for(int i=0; i<Camera.getNumberOfCameras(); i++) {
                mCompressedWeights[i] = null;
            }
        }
        return true;
    }
}
