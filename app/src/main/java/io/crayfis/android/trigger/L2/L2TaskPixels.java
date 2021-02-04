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

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "default";
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
    }

    private final Config mConfig;
    private final ScriptC_l2Trigger mTrigger;
    private final Lock mLock = new ReentrantLock();

    L2TaskPixels(TriggerProcessor processor, Config cfg) {
        super(processor);
        mConfig = cfg;

        RenderScript rs = processor.application.getRenderScript();
        mTrigger = new ScriptC_l2Trigger(rs);
        Allocation pixIdx = Allocation.createSized(rs, Element.U32(rs), mConfig.npix, Allocation.USAGE_SCRIPT);
        Allocation pixVal = Allocation.createSized(rs, Element.U32(rs), mConfig.npix, Allocation.USAGE_SCRIPT);
        Allocation pixN = Allocation.createSized(rs, Element.U32(rs), 1, Allocation.USAGE_SCRIPT);

        mTrigger.invoke_set_L2Thresh(mConfig.thresh);
        mTrigger.set_gMaxN(mConfig.maxn);
        mTrigger.set_gNPixMax(mConfig.npix);
        mTrigger.bind_gPixIdx(pixIdx);
        mTrigger.bind_gPixVal(pixVal);
        mTrigger.bind_gPixN(pixN);

        mTrigger.set_gWeights(processor.xb.weights);
    }


    protected int processFrame(Frame frame) {

        L2Processor.L2Count++;

        ArrayList<DataProtos.Pixel> pixels = new ArrayList<>();
        List<Pair<Integer, Integer>> l2PixelCoords = getL2PixelCoords(frame, mTrigger, mLock);

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
                    int idx = dx + 5*dy;
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

    static List<Pair<Integer, Integer>> getL2PixelCoords(Frame frame,
                                                         ScriptC_l2Trigger trig,
                                                         Lock lock) {

        List<Pair<Integer, Integer>> l2Coords = new ArrayList<>();
        Allocation buf = frame.getAllocation();
        int[] pixN = new int[1];

        lock.lock();

        if (frame.getFormat() == Frame.Format.RAW) {
            trig.forEach_trigger_uchar(buf);
        } else {
            trig.forEach_trigger_uchar(buf);
        }

        // first count the number of hits and construct buffers
        trig.get_gPixN().copyTo(pixN);
        int[] pixIdx = new int[pixN[0]];
        trig.get_gPixIdx().copy1DRangeTo(0, pixN[0], pixIdx);

        lock.unlock();

        // now split coords into x and y
        for (int i = 0; i < pixN[0]; i++) {
            int x = pixIdx[i] % frame.getWidth();
            int y = pixIdx[i] / frame.getWidth();
            l2Coords.add(Pair.create(x, y));
        }


        return l2Coords;
    }
}
