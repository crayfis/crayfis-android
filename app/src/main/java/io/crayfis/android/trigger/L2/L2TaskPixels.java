package io.crayfis.android.trigger.L2;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.crayfis.android.DataProtos;
import io.crayfis.android.ScriptC_l2Trigger;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.ui.navdrawer.data.LayoutData;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
class L2TaskPixels extends TriggerProcessor.Task {

    static class Config extends L2Processor.Config {

        static final String NAME = "pixels";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(L2Processor.KEY_L2_THRESH, 255);
            KEY_DEFAULT.put(L2Processor.KEY_NPIX, 120);
            KEY_DEFAULT.put(L2Processor.KEY_MAXN, false);
        }

        final int thresh;
        final int npix;
        final boolean maxn;

        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            thresh = getInt(L2Processor.KEY_L2_THRESH);
            npix = getInt(L2Processor.KEY_NPIX);
            maxn = getBoolean(L2Processor.KEY_MAXN);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L2Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new L2TaskPixels(processor, this);
        }

        @Override
        public int generateL2Threshold(float l1Thresh) {
            if(l1Thresh > 3) // minimum threshold where we want to prescale
                return (int) Math.ceil(l1Thresh) - 1;
            return (int) Math.ceil(l1Thresh);
        }
    }

    private final Config mConfig;
    private final ScriptC_l2Trigger mTrigger;
    private final Lock mLock = new ReentrantLock();

    private final Allocation aPixIdx;
    private final Allocation aPixVal;
    private final Allocation aPixN;

    L2TaskPixels(TriggerProcessor processor, Config cfg) {
        super(processor);
        mConfig = cfg;

        RenderScript rs = processor.application.getRenderScript();
        mTrigger = new ScriptC_l2Trigger(rs);
        aPixIdx = Allocation.createSized(rs, Element.U32(rs), mConfig.npix, Allocation.USAGE_SCRIPT);
        aPixVal = Allocation.createSized(rs, Element.U32(rs), mConfig.npix, Allocation.USAGE_SCRIPT);
        aPixN = Allocation.createSized(rs, Element.U32(rs), 1, Allocation.USAGE_SCRIPT);

        mTrigger.invoke_set_L2Thresh(mConfig.thresh);
        mTrigger.set_gMaxN(mConfig.maxn);
        mTrigger.set_gNPixMax(mConfig.npix);
        mTrigger.set_gResX(processor.xb.res_x);
        mTrigger.set_gWeights(processor.xb.weights);
        mTrigger.bind_gPixIdx(aPixIdx);
        mTrigger.bind_gPixVal(aPixVal);
        mTrigger.bind_gPixN(aPixN);

        // make sure pixN is initialized to 0
        mTrigger.invoke_reset();
    }


    protected int processFrame(Frame frame) {

        L2Processor.L2Count++;

        ArrayList<DataProtos.Pixel> pixels = new ArrayList<>();
        List<Pair<Integer, Integer>> l2PixelCoords = getL2PixelCoords(frame);

        for(Pair<Integer, Integer> xy: l2PixelCoords) {

            int ix = xy.first;
            int iy = xy.second;

            short[] regionBuf = new short[25];
            frame.copyRegion(ix, iy, 2, 2, regionBuf, 0);
            short val = regionBuf[12];

            CFLog.d("val = " + val + " at (" + ix + "," + iy +")");

            LayoutData.appendData(val);

            double sum3 = 0;
            double sum5 = 0;
            int nearMax = 0;

            for(int dx = -2; dx <= 2; dx++) {
                for(int dy = -2; dy <= 2; dy++) {
                    int idx = (dx+2) + 5*(dy+2);
                    int ival = regionBuf[idx];
                    sum5 += ival;
                    if(Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                        sum3 += ival;
                    }
                    if(ival > nearMax) {
                        nearMax = ival;
                    }
                }
            }

            DataProtos.Pixel.Builder pixBuilder = DataProtos.Pixel.newBuilder();

            pixBuilder.setX(ix)
                    .setY(iy)
                    .setVal(val)
                    .setAvg3((float)(sum3 / 9))
                    .setAvg5((float)(sum5 / 25))
                    .setNearMax(nearMax);

            pixels.add(pixBuilder.build());

        }

        frame.setPixels(pixels);

        return l2PixelCoords.size();
    }

    private List<Pair<Integer, Integer>> getL2PixelCoords(Frame frame) {

        List<Pair<Integer, Integer>> l2Coords = new ArrayList<>();
        Allocation buf = frame.getAllocation();
        int[] pixN = new int[1];

        mLock.lock();

        if (frame.getFormat() == Frame.Format.RAW) {
            mTrigger.forEach_trigger_ushort(buf);
        } else {
            mTrigger.forEach_trigger_uchar(buf);
        }

        // first count the number of hits and construct buffers
        aPixN.copyTo(pixN);

        if(pixN[0] == 0) {
            CFLog.e("No triggers found!");
            mTrigger.invoke_reset();
            mLock.unlock();
            return l2Coords;
        }

        int[] pixIdx = new int[pixN[0]];
        aPixIdx.copy1DRangeTo(0, pixN[0], pixIdx);

        mTrigger.invoke_reset();

        mLock.unlock();

        // now split coords into x and y
        for (int i = 0; i < pixN[0]; i++) {
            int x = pixIdx[i] % frame.getWidth();
            int y = pixIdx[i] / frame.getWidth();
            l2Coords.add(Pair.create(x, y));
            //CFLog.d("rs (" + x + ", " + y + ", " + pixVal[i] + ")");
        }

        return l2Coords;
    }
}
