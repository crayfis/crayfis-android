package io.crayfis.android.trigger.L2;

import android.util.Pair;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.ui.navdrawer.data.LayoutData;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 1/6/18.
 */

class L2TaskByteBlock extends L2Task {

    static class Config extends L2Config {
        static final int DEFAULT_NPIX = 500;
        static final int DEFAULT_RADIUS = 2;

        final int npix;
        final int radius;
        Config(String name, String cfg) {
            super(name, cfg);

            // FIXME: there's probably an easier/more generic way to parse simple key-val pairs.
            int cfg_npix = DEFAULT_NPIX;
            int cfg_radius = DEFAULT_RADIUS;
            for (String c : cfg.split(";")) {
                String[] kv = c.split("=");
                if (kv.length != 2) continue;
                String key = kv[0];
                String value = kv[1];

                switch (key) {
                    case "npix":
                        try {
                            cfg_npix = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            CFLog.w("Couldn't parse npix argument for L2 configuraion!");
                        }
                        break;
                    case "radius":
                        try {
                            cfg_radius = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            CFLog.w("Couldn't parse radius argument for L2 configuration!");
                        }
                        break;
                }
            }

            npix = cfg_npix;
            radius = cfg_radius;
        }

        @Override
        public L2Task makeTask(L2Processor l2Processor, RawCameraFrame frame) {
            return new L2TaskByteBlock(l2Processor, frame, npix, radius);
        }
    }

    private final int radius;

    L2TaskByteBlock(L2Processor processor, RawCameraFrame frame, int npix, int radius) {

        super(processor, frame, npix);
        this.radius = radius;

    }

    @Override
    void buildPixels(RawCameraFrame frame) {

        DataProtos.ByteBlock.Builder builder = DataProtos.ByteBlock.newBuilder();

        Mat grayMat = frame.getGrayMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();

        ExposureBlock xb = frame.getExposureBlock();

        Imgproc.threshold(grayMat, threshMat, xb.getL2Thresh(), 0, Imgproc.THRESH_TOZERO);
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
                for(int dy=Math.max(0,iy-radius); dy<=Math.min(height-1, iy+radius); dy++) {
                    for(int dx=Math.max(0,ix-radius); dx<=Math.min(width-1, ix+radius); dx++) {
                        blockXY.add(Pair.create(dx, dy));
                    }
                }

            } catch (OutOfMemoryError e) {
                    CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
            }

        }

        for(Pair<Integer, Integer> pairXY : blockXY) {
            builder.addVal(mFrame.getRawByteAt(pairXY.first, pairXY.second));
        }

        mL2Processor.pass += l2PixelCoords.total();
        l2PixelCoords.release();

        frame.setByteBlock(builder.build());
    }
}
