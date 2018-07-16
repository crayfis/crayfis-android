package io.crayfis.android.trigger.L2;

import android.util.Pair;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.LinkedHashSet;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.ui.navdrawer.data.LayoutData;

/**
 * Created by jswaney on 1/6/18.
 */

class L2TaskByteBlock extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "byteblock";
        static final HashMap<String, Object> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put(L2Processor.KEY_L2_THRESH, 255);
            KEY_DEFAULT.put(L2Processor.KEY_NPIX, 120);
            KEY_DEFAULT.put(L2Processor.KEY_RADIUS, 2);
        }

        final int thresh;
        final int npix;
        final int radius;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            thresh = getInt(L2Processor.KEY_L2_THRESH);
            npix = getInt(L2Processor.KEY_NPIX);
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
    }

    private final Config mConfig;

    L2TaskByteBlock(TriggerProcessor processor, Config cfg) {
        super(processor);
        mConfig = cfg;

    }

    @Override
    protected int processFrame(RawCameraFrame frame) {

        L2Processor.L2Count++;

        DataProtos.ByteBlock.Builder builder = DataProtos.ByteBlock.newBuilder();
        builder.setSideLength(2*mConfig.radius + 1);

        Mat grayMat = frame.getGrayMat();
        if(grayMat == null) return 0;
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();

        Imgproc.threshold(grayMat, threshMat, mConfig.thresh, 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);
        threshMat.release();

        LinkedHashSet<Pair<Integer, Integer>> blockXY = new LinkedHashSet<>();

        for(int i=0; i<l2PixelCoords.total(); i++) {

            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = frame.getRawValAt(ix, iy);
            builder.addX(ix)
                    .addY(iy);

            LayoutData.appendData(val);

            for(int dy=Math.max(0,iy-mConfig.radius); dy<=Math.min(height-1, iy+mConfig.radius); dy++) {
                for(int dx=Math.max(0,ix-mConfig.radius); dx<=Math.min(width-1, ix+mConfig.radius); dx++) {
                    blockXY.add(Pair.create(dx, dy));
                }
            }

        }

        for(Pair<Integer, Integer> pairXY : blockXY) {
            builder.addVal(frame.getRawValAt(pairXY.first, pairXY.second));
        }

        l2PixelCoords.release();

        frame.setByteBlock(builder.build());

        return (int)l2PixelCoords.total();
    }
}
