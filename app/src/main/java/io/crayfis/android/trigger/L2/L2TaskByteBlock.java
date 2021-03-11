package io.crayfis.android.trigger.L2;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * Created by jswaney on 1/6/18.
 */

class L2TaskByteBlock extends TriggerProcessor.Task {

    static class Config extends L2Processor.Config {

        static final String NAME = "byteblock";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(L2Processor.KEY_L2_THRESH, 255);
            KEY_DEFAULT.put(L2Processor.KEY_NPIX, 120);
            KEY_DEFAULT.put(L2Processor.KEY_MAXN, false);
            KEY_DEFAULT.put(L2Processor.KEY_RADIUS, 2);
        }

        final int thresh;
        final int npix;
        final boolean maxn;
        final int radius;

        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            thresh = getInt(L2Processor.KEY_L2_THRESH);
            npix = getInt(L2Processor.KEY_NPIX);
            maxn = getBoolean(L2Processor.KEY_MAXN);
            radius = getInt(L2Processor.KEY_RADIUS);
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L2Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor) {
            return new L2TaskByteBlock(processor, this);
        }

        @Override
        public int generateL2Threshold(float l1Thresh) {
            return (int) l1Thresh;
        }
    }

    private final Config mConfig;
    private final int mSideLength;
    private final ScriptC_l2Trigger mTrigger;
    private final Lock mLock = new ReentrantLock();

    private final Allocation aPixIdx;
    private final Allocation aPixVal;
    private final Allocation aPixN;


    L2TaskByteBlock(TriggerProcessor processor, Config cfg) {
        super(processor);
        mConfig = cfg;
        mSideLength = 2*mConfig.radius + 1;

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

    @Override
    protected int processFrame(Frame frame) {

        L2Processor.L2Count++;

        DataProtos.ByteBlock.Builder builder = DataProtos.ByteBlock.newBuilder();
        builder.setSideLength(mSideLength);

        LinkedHashSet<Pair<Integer, Integer>> blockXY = new LinkedHashSet<>();

        List<Pair<Integer, Integer>> l2PixelCoords = getL2PixelCoords(frame);

        for(Pair<Integer, Integer> xy : l2PixelCoords) {

            int ix = xy.first;
            int iy = xy.second;

            // copy region from frame
            short[] regionBuf = new short[mSideLength*mSideLength];
            frame.copyRegion(ix, iy, 2, 2, regionBuf, 0);

            builder.addX(ix)
                    .addY(iy);

            short val = regionBuf[regionBuf.length / 2];
            LayoutData.appendData(val);

            // add pixels not yet in the ByteBlock
            for(int dy=-mConfig.radius; dy<=mConfig.radius; dy++) {
                for(int dx=-mConfig.radius; dx<=mConfig.radius; dx++) {
                    Pair<Integer, Integer> coords = Pair.create(ix+dx, iy+dy);
                    if(!blockXY.contains(coords)) {
                        blockXY.add(coords);
                        val = regionBuf[(dx+mConfig.radius) + mSideLength*(dy+mConfig.radius)];
                        if(val >= 0) {
                            builder.addVal(val);
                        }
                    }
                }
            }

        }

        frame.setByteBlock(builder.build());

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
