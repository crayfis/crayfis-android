package io.crayfis.android.trigger;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/16/16.
 */
public class L2TaskMaxN extends L2Task {
    public static class Config extends L2Config {
        public static final int DEFAULT_NPIX = 25;

        public final int npix;
        Config(String name, String cfg) {
            super(name, cfg);

            // FIXME: there's probably an easier/more generic way to parse simple key-val pairs.
            int cfg_npix = DEFAULT_NPIX;
            for (String c : cfg.split(";")) {
                String[] kv = c.split("=");
                CFLog.i("parsing c='" + c + "', split len = " + kv.length);
                if (kv.length != 2) continue;
                String key = kv[0];
                String value = kv[1];
                CFLog.i("key='" + key +"', val='" + value +"'");

                if (key.equals("npix")) {
                    try {
                        cfg_npix = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        CFLog.w("Couldn't parse npix argument for L2 configuraion!");
                        continue;
                    }
                }
            }

            npix = cfg_npix;
        }

        @Override
        public L2Task makeTask(L2Processor l2Processor, RawCameraFrame frame) {
            return new L2TaskMaxN(l2Processor, frame, npix);
        }
    }

    public static final PixelComparator PIXEL_COMPARATOR = new PixelComparator();
    private final int mNpix;

    private L2TaskMaxN(L2Processor l2processor, RawCameraFrame frame, int npix) {
        super(l2processor, frame, null);
        mNpix = npix;
    }

    private static class PixelComparator implements Comparator<RecoPixel> {

        @Override
        public int compare(RecoPixel recoPixel, RecoPixel t1) {
            return t1.val - recoPixel.val;
        }
    }
    private void prunePixels(ArrayList<RecoPixel> pixels, int N) {
        if (pixels.size() <= N) { return; }
        //Collections.sort(pixels, PIXEL_COMPARATOR);
        pixels.subList(N, pixels.size()).clear();
    }

    @Override
    ArrayList<RecoPixel> buildPixels() {
        ArrayList<RecoPixel> pixels = new ArrayList<>();


        Mat grayMat = mFrame.getGrayMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();

        ExposureBlock xb = mFrame.getExposureBlock();

        Imgproc.threshold(grayMat, threshMat, xb.L2_threshold-1, 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);
        threshMat.release();

        for(int i=0; i<l2PixelCoords.total(); i++) {

            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = mFrame.getRawByteAt(ix, iy) & 0xFF;
            int adjustedVal = (int) grayMat.get(iy, ix)[0];

            RecoPixel p;

            L2Processor.histL2Pixels.fill(adjustedVal);
            try {
                p = new RecoPixel();
            } catch (OutOfMemoryError e) {
                CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
                mEvent.npix_dropped++;
                continue;
            }

            p.x = ix;
            p.y = iy;
            p.val = val;
            p.adjusted_val = adjustedVal;


            //
            Mat grayAvg3 = grayMat.submat(Math.max(iy-1,0), Math.min(iy+2,height),
                    Math.max(ix-1,0), Math.min(ix+2,width));
            Mat grayAvg5 = grayMat.submat(Math.max(iy-2,0), Math.min(iy+3,height),
                    Math.max(ix-2,0), Math.min(ix+3,width));

            p.avg_3 = (float)Core.mean(grayAvg3).val[0];
            p.avg_5 = (float)Core.mean(grayAvg5).val[0];
            p.near_max = (int)Core.minMaxLoc(grayAvg3).maxVal;

            grayAvg3.release();
            grayAvg5.release();

            pixels.add(p);
            Collections.sort(pixels, PIXEL_COMPARATOR);

            prunePixels(pixels, mNpix);

        }

        return pixels;
    }
}
