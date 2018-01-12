package io.crayfis.android.trigger.L2;

import android.util.Pair;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.LinkedHashSet;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.ui.navdrawer.data.LayoutData;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by jswaney on 1/6/18.
 */

class L2TaskByteBlock extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {
        static final int DEFAULT_NPIX = 500;
        static final int DEFAULT_RADIUS = 2;
        static final int DEFAULT_THRESH = 255;

        final int thresh;
        final int npix;
        final int radius;
        Config(String name, HashMap<String, String> options) {
            super(name, options);

            thresh = CFUtil.getInt(options.get("thresh"), DEFAULT_THRESH);
            npix = CFUtil.getInt(options.get("npix"), DEFAULT_NPIX);
            radius = CFUtil.getInt(options.get("radius"), DEFAULT_RADIUS);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new L2TaskByteBlock(processor, frame, this);
        }
    }

    private final Config mConfig;

    L2TaskByteBlock(TriggerProcessor processor, RawCameraFrame frame, Config cfg) {

        super(processor, frame);
        mConfig = cfg;

    }

    @Override
    public boolean processFrame(RawCameraFrame frame) {

        L2Processor.L2Count++;

        DataProtos.ByteBlock.Builder builder = DataProtos.ByteBlock.newBuilder();

        Mat grayMat = frame.getGrayMat();
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
            int val = frame.getRawByteAt(ix, iy) & 0xFF;
            builder.addX(ix)
                    .addY(iy);

            LayoutData.appendData(val);

            try {
                for(int dy=Math.max(0,iy-mConfig.radius); dy<=Math.min(height-1, iy+mConfig.radius); dy++) {
                    for(int dx=Math.max(0,ix-mConfig.radius); dx<=Math.min(width-1, ix+mConfig.radius); dx++) {
                        blockXY.add(Pair.create(dx, dy));
                    }
                }

            } catch (OutOfMemoryError e) {
                    CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
            }

        }

        for(Pair<Integer, Integer> pairXY : blockXY) {
            builder.addVal(frame.getRawByteAt(pairXY.first, pairXY.second));
        }

        mProcessor.pass += l2PixelCoords.total();
        l2PixelCoords.release();

        frame.setByteBlock(builder.build());

        return true;
    }
}
