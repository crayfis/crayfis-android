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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.ScriptC_sumFrames;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 7/18/2017.
 */

class StatsTask extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "stats";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(KEY_MAXFRAMES, 25000);
            KEY_DEFAULT.put(PreCalibrator.KEY_WEIGHT_GRID_SIZE, 1500);
            KEY_DEFAULT.put(PreCalibrator.KEY_HOTCELLS_N_DEVIATIONS, 10.f);
            KEY_DEFAULT.put(PreCalibrator.KEY_HOTCELL_LIMIT, .01f);
        }

        final int frames;
        final int gridSize;
        final double nDeviations;
        final double hotcellLimit;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            frames = getInt(KEY_MAXFRAMES);
            gridSize = getInt(PreCalibrator.KEY_WEIGHT_GRID_SIZE);
            nDeviations = getFloat(PreCalibrator.KEY_HOTCELLS_N_DEVIATIONS);
            hotcellLimit = getFloat(PreCalibrator.KEY_HOTCELL_LIMIT);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            //FIXME: what to do with this?
            return null;
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new StatsTask(processor, this);
        }
    }

    private Allocation mSumAlloc;
    private Allocation mSsqAlloc;
    private ScriptC_sumFrames mScriptCSumFrames;
    private final RenderScript RS;

    private final boolean mRAW = DAQManager.getInstance().isStreamingRAW();
    private final Config mConfig;

    private int mNCut = 0;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private static final String FORMAT = ".jpeg";

    StatsTask(TriggerProcessor processor, Config config) {
        super(processor);

        mConfig = config;

        RS = processor.application.getRenderScript();

        CFLog.i("StatsTask created");
        mScriptCSumFrames = new ScriptC_sumFrames(RS);
        DAQManager daq = DAQManager.getInstance();

        Type type = new Type.Builder(RS, Element.I32(RS))
                .setX(daq.getResX())
                .setY(daq.getResY())
                .create();

        mSumAlloc = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        mSsqAlloc = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        mScriptCSumFrames.set_gSum(mSumAlloc);
        mScriptCSumFrames.set_gSsq(mSsqAlloc);
    }

    /**
     * Performs a running element-wise addition for each pixel in the frame
     *
     * @param frame Frame
     * @return true if we have reached the requisite number of frames, false otherwise
     */
    @Override
    protected int processFrame(Frame frame) {
        if(frame.getExposureBlock().count.intValue()
                % (CONFIG.getTargetFPS()*CONFIG.getExposureBlockPeriod()) == 0) {
            mProcessor.application.checkBatteryStats();
        }
        if(mRAW)
            mScriptCSumFrames.forEach_update_ushort(frame.getAllocation());
        else
            mScriptCSumFrames.forEach_update_uchar(frame.getAllocation());
        return 1;
    }

    /**
     * Calculates weights based on running sum, then compresses and stores them in a protobuf file
     * and in the SharedPreferences
     */
    @Override
    protected void onMaxReached() {

        int width = mSumAlloc.getType().getX();
        int height = mSumAlloc.getType().getY();

        mScriptCSumFrames.set_gTotalFrames(mConfig.frames);

        // kill hotcells by mean and variance

        boolean[] hotcellArray = new boolean[width*height];
        Set<Integer> hotcellSet = new HashSet<>(PreCalibrator.BUILDER.getHotcellList());
        for(int hxy : hotcellSet) {
            hotcellArray[hxy] = true;
        }

        Allocation copyAlloc = Allocation.createTyped(RS, new Type.Builder(RS, Element.F32(RS))
                .setX(width)
                .setY(height)
                .create(), Allocation.USAGE_SCRIPT);

        float[] meanArray = new float[width*height];
        mScriptCSumFrames.forEach_find_mean(copyAlloc);
        copyAlloc.copyTo(meanArray);

        float[] varArray = new float[width*height];
        mScriptCSumFrames.forEach_find_var(copyAlloc);
        copyAlloc.copyTo(varArray);
        copyAlloc.destroy();

        mSsqAlloc.destroy();

        if(isBimodal(varArray, meanArray)) {
            CFLog.d("Bimodal mean and var");
            double gainMean = 0;
            double n = 0;
            for(int i=0; i<varArray.length; i++) {
                if(!hotcellArray[i] && meanArray[i] != 0) {
                    gainMean += varArray[i] / meanArray[i];
                    n++;
                }
            }
            if(n > 0) gainMean /= n;

            boolean[] mask = hotcellArray.clone();
            for(int i=0; i<hotcellArray.length; i++) {
                mask[i] = hotcellArray[i] || (varArray[i] < meanArray[i] * gainMean);
            }
            recursiveCut(mConfig.nDeviations, mask, meanArray, varArray);
            float[] residuals = findMedianDiffs(meanArray, varArray, mask, 100);
            recursiveCut(mConfig.nDeviations, mask, residuals);

            for(int i=0; i<hotcellArray.length; i++) {
                hotcellArray[i] = hotcellArray[i] || mask[i] && varArray[i] < meanArray[i] * gainMean;
            }

        } else {
            recursiveCut(mConfig.nDeviations, hotcellArray, meanArray, varArray);
            float[] residuals = findMedianDiffs(meanArray, varArray, hotcellArray, 100);
            recursiveCut(mConfig.nDeviations, hotcellArray, residuals);
        }

        for(int i=0; i<hotcellArray.length; i++) {
            if(hotcellArray[i]) hotcellSet.add(i);
        }

        for(int pos: hotcellSet) {
            int x = pos % width;
            int y = pos / width;
            mScriptCSumFrames.invoke_killHotcell(x, y);
        }

        // next, find weights
        // first, find appropriate dimensions to downsample

        int sampleStep = getSampleStep(width, height, mConfig.gridSize);
        mScriptCSumFrames.set_sampleStep(sampleStep);

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
        mScriptCSumFrames.forEach_downsampleSums(downsampledAlloc);
        mSumAlloc.destroy();
        float[] downsampleArray = new float[sampleResX * sampleResY];
        downsampledAlloc.copyTo(downsampleArray);

        float minAvg = 256f;
        for (float avg : downsampleArray) {
            if (avg < minAvg) {
                minAvg = avg;
            }
        }
        CFLog.d("minSum = " + minAvg*sampleStep*sampleStep*mConfig.frames);

        // we use log(1 + 1/mu) as our weights, which takes into account the nonlinearity of
        // truncation on the sums, assuming exponentially distributed noise for simplicity
        double maxWeight = Math.log1p(1f/minAvg);

        CFLog.d("maxWeight = " + maxWeight);

        mScriptCSumFrames.set_gMaxWeight((float)maxWeight);
        mScriptCSumFrames.forEach_normalizeWeights(downsampledAlloc, byteAlloc);

        byte[] byteNormalizedArray = new byte[sampleResX * sampleResY];
        byteAlloc.copyTo(byteNormalizedArray);

        // compress with OpenCV
        MatOfByte downsampledBytes = new MatOfByte(byteNormalizedArray);
        Mat downsampleMat2D = downsampledBytes.reshape(0, sampleResY);
        MatOfByte buf = new MatOfByte();
        int[] paramArray = new int[]{Imgcodecs.CV_IMWRITE_JPEG_QUALITY, 100};
        MatOfInt params = new MatOfInt(paramArray);
        Imgcodecs.imencode(FORMAT, downsampleMat2D.t(), buf, params);
        byte[] bytes = buf.toArray();
        CFLog.d("Compressed bytes = " + bytes.length);

        downsampledBytes.release();
        downsampleMat2D.release();
        buf.release();
        params.release();

        // now store weights in PreCalibrationResult
        PreCalibrator.BUILDER
                .setCompressedWeights(ByteString.copyFrom(bytes))
                .setCompressedFormat(FORMAT)
                .setResX(width)
                .setResY(height)
                .addAllHotcell(hotcellSet);

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

    private boolean isBimodal(float[] num, float[] denom) {
        double sum = 0;
        double ssq = 0;
        int n = num.length;

        for(int i=0; i<n; i++) {
            double frac = num[i] / denom[i];
            sum += frac;
            ssq += frac*frac;
        }

        double totalMean = sum / n;

        double upperSum = 0;
        double upperSsq = 0;
        int upperN = 0;

        for(int i=0; i<num.length; i++) {
            double frac = num[i] / denom[i];
            if(frac > totalMean) {
                upperSum += frac;
                upperSsq += frac*frac;
                upperN++;
            }
        }

        double upperMean = upperSum / upperN;
        double upperDev = Math.sqrt(upperSsq / upperN - upperMean*upperMean);

        double lowerMean = (sum - upperSum) / (n - upperN);
        double lowerDev = Math.sqrt((ssq - upperSsq) / (n - upperN) - lowerMean*lowerMean);

        return upperMean - lowerMean > upperDev + lowerDev;
    }

    private void recursiveCut(double nSigmas, boolean[] cut, float[]... data) {

        int nFeatures = data.length;
        int nEntries = data[0].length;

        double[] thresholds = new double[nFeatures];
        double[] sum = new double[nFeatures];
        double[] ssq = new double[nFeatures];
        int n=0;

        for(int j=0; j<nEntries; j++) {

            if(cut[j]) continue;

            n++;

            for(int i=0; i<nFeatures; i++) {
                sum[i] += data[i][j];
                ssq[i] += data[i][j] * data[i][j];
            }
        }

        for(int i=0; i<nFeatures; i++) {
            double mean = sum[i] / n;
            double dev = Math.sqrt(ssq[i] / n - mean*mean);
            thresholds[i] = mean + nSigmas * dev;
        }

        boolean newCut;
        do {
            CFLog.d("n = " + n);
            for(int i=0; i<nFeatures; i++) {
                CFLog.d("Thresh " + i + " = " + thresholds[i]);
            }

            newCut = false;

            for(int j=0; j<nEntries; j++) {
                for(int i=0; i<nFeatures; i++) {
                    if(!cut[j] && data[i][j] > thresholds[i]) {
                        newCut = true;
                        cut[j] = true;
                        mNCut++;
                        for(int k=0; k<nFeatures; k++) {
                            sum[k] -= data[k][j];
                            ssq[k] -= data[k][j] * data[k][j];
                        }
                        n--;
                    }
                }
                if(mNCut > nEntries * mConfig.hotcellLimit) {
                    CFLog.d("mNCut = " + mNCut);
                    return;
                }
            }

            for(int i=0; i<nFeatures; i++) {
                double mean = sum[i] / n;
                double dev = Math.sqrt(ssq[i] / n - mean*mean);
                thresholds[i] = mean + nSigmas * dev;
            }

        } while(newCut);
    }

    private float[] findMedianDiffs(float[] x, float[] y, boolean[] cut, int nPoints) {

        int nUncut = x.length;
        Integer[] indices = new Integer[x.length];
        for(int i=0; i<indices.length; i++) {
            indices[i] = i;
            if(cut[i]) nUncut--;
        }

        Arrays.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer i0, Integer i1) {
                if(cut[i0] && !cut[i1]) return 1;
                if(cut[i1] && !cut[i0]) return -1;

                return (int)Math.signum(x[i0] - x[i1]);
            }
        });

        //ArrayList<Float> xMedians = new ArrayList<>();
        ArrayList<Float> yMedians = new ArrayList<>();

        for(int sample=0; sample < nPoints; sample++) {
            int start = sample * nUncut / nPoints;
            int end = (sample+1) * nUncut / nPoints;

            //xMedians.add(x[indices[(start + end)/2]]);

            float[] yVals = new float[end - start + 1];
            for(int i=start; i<end; i++) {
                yVals[i-start] = y[indices[i]];
            }
            Arrays.sort(yVals);

            yMedians.add(yVals[yVals.length/2]);
        }

        CFLog.d(yMedians.toString());

        //xMedians.add(-1f);
        yMedians.add(-1f);

        float[] residuals = new float[x.length];
        for(int i=0; i<residuals.length; i++) {
            int j = indices[i];
            float median = yMedians.get(Math.min(i * nPoints / nUncut, nPoints));
            residuals[j] = y[j] - median;
        }

        return residuals;

    }

}
