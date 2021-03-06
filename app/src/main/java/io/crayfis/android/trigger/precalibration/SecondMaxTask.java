package io.crayfis.android.trigger.precalibration;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.ScriptC_findSecond;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 6/6/2017.
 */

class SecondMaxTask extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "secondmax";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(KEY_MAXFRAMES, 50000);
            KEY_DEFAULT.put(PreCalibrator.KEY_INTEGRAL_THRESH, .01f); // max number of hotcells
            KEY_DEFAULT.put(PreCalibrator.KEY_DIFFERENTIAL_THRESH, .02f); // max number per bin in second max distribution
        }

        final float integralThresh;
        final float differentialThresh;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            integralThresh = getFloat(PreCalibrator.KEY_INTEGRAL_THRESH);
            differentialThresh = getFloat(PreCalibrator.KEY_DIFFERENTIAL_THRESH);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            //FIXME: what to do with this?
            return null;
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new SecondMaxTask(processor, this);
        }
    }

    private final Set<Integer> HOTCELL_COORDS;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private final boolean mRAW = DAQManager.getInstance().isStreamingRAW();

    private final RenderScript RS;
    private final ScriptC_findSecond mScriptCFindSecond;
    private final Allocation aMax;
    private final Allocation aSecond;

    private final Config mConfig;

    SecondMaxTask(TriggerProcessor processor, Config config) {
        super(processor);

        mConfig = config;

        RS = mProcessor.application.getRenderScript();
        CFLog.i("SecondMaxTask created");
        mScriptCFindSecond = new ScriptC_findSecond(RS);

        DAQManager daq = DAQManager.getInstance();

        Type type = new Type.Builder(RS, Element.U8(RS))
                .setX(daq.getResX())
                .setY(daq.getResY())
                .create();
        aMax = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        aSecond = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        mScriptCFindSecond.set_aMax(aMax);
        mScriptCFindSecond.set_aSecond(aSecond);
        if(processor.xb.weights != null)
            mScriptCFindSecond.set_gWeights(processor.xb.weights);
        HOTCELL_COORDS = new HashSet<>();
    }

    /**
     * Keeps allocations of running largest and second-largest values for each pixel
     *
     * @param frame Frame
     * @return true if we have reached the requisite number of frames, false otherwise
     */
    @Override
    protected int processFrame(Frame frame) {
        if(mRAW)
            mScriptCFindSecond.forEach_order_ushort(frame.getAllocation());
        else
            mScriptCFindSecond.forEach_order_uchar(frame.getAllocation());
        if(frame.getExposureBlock().count.intValue()
                % (CONFIG.getTargetFPS()*CONFIG.getExposureBlockPeriod()) == 0) {
            mProcessor.application.checkBatteryStats();
        }
        return 1;
    }

    @Override
    protected void onMaxReached() {

        // set up Allocations for histogram
        ScriptIntrinsicHistogram histogram = ScriptIntrinsicHistogram.create(RS, Element.U8(RS));
        Allocation aout = Allocation.createSized(RS, Element.U32(RS), 256, Allocation.USAGE_SCRIPT);
        histogram.setOutput(aout);
        histogram.forEach(aSecond);

        int[] secondHist = new int[256];
        aout.copyTo(secondHist);

        for(int i=0; i<secondHist.length; i++) {
            if (secondHist[i] != 0) {
                CFLog.d("hist[" + i + "] = " + secondHist[i]);
            }
        }

        // find minimum value in aSecond considered as "hot"
        int area = aMax.getType().getX() * aMax.getType().getY();
        int integralLimit = (int) (mConfig.integralThresh * area);
        int differentialLimit = (int) (mConfig.differentialThresh * area);
        int pixKilled = 0;

        int cutoff=255;
        while(pixKilled < integralLimit && cutoff > 0) {
            if(secondHist[cutoff] > differentialLimit) {
                cutoff++;
                break;
            }
            pixKilled += secondHist[cutoff];
            cutoff--;
        }
        CFLog.d("cutoff = " + cutoff);

        // copy to OpenCV to locate hot pixels
        byte[] maxArray = new byte[area];
        aMax.copyTo(maxArray);
        aMax.destroy();

        byte[] secondArray = new byte[area];
        aSecond.copyTo(secondArray);
        aSecond.destroy();

        MatOfByte secondMat = new MatOfByte(secondArray);

        Mat threshMat = new Mat();
        Mat hotcellCoordsMat = new Mat();

        Imgproc.threshold(secondMat, threshMat, cutoff-1, 0, Imgproc.THRESH_TOZERO);
        secondMat.release();
        Core.findNonZero(threshMat, hotcellCoordsMat);
        threshMat.release();

        for(int i=0; i<hotcellCoordsMat.total(); i++) {
            double[] hotcellCoords = hotcellCoordsMat.get(i,0);
            int pos = (int) hotcellCoords[1];
            int width = aSecond.getType().getX();
            int x = pos % width;
            int y = pos / width;

            HOTCELL_COORDS.add(pos);

            // check pixels adjacent to hotcells, and see if max values are above the cutoff
            for(int dx=x-1; dx<=x+1; dx++) {
                for(int dy=y-1; dy<=y+1; dy++) {
                    try {
                        int adjMax = maxArray[dx + width * dy] & 0xFF;
                        if (adjMax >= cutoff) {
                            HOTCELL_COORDS.add(dx + width * dy);
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // don't crash if we're on the border
                    }
                }
            }
        }

        // store in CFConfig and protobuf file
        CFLog.d("Total hotcells found: " + HOTCELL_COORDS.size());

        for(Integer pos: HOTCELL_COORDS) {
            PreCalibrator.BUILDER.addHotcell(pos);
        }

        int maxNonZero = 255;
        while (secondHist[maxNonZero] == 0) {
            maxNonZero--;
        }
        for (int i = 0; i <= maxNonZero; i++) {
            PreCalibrator.BUILDER.addSecondHist(secondHist[i]);
        }
    }
}

