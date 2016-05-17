package edu.uci.crayfis.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

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
            CFLog.i("Setting npix = " + npix + " at L2.");
        }

        @Override
        public L2Task makeTask(L2Processor l2Processor, RawCameraFrame frame) {
            return new L2TaskMaxN(l2Processor, frame, npix);
        }
    }

    public static final PixelComparator PIXEL_COMPARATOR = new PixelComparator();
    private final int mNpix;

    L2TaskMaxN(L2Processor l2processor, RawCameraFrame frame, int npix) {
        super(l2processor, frame);
        mNpix = npix;
    }

    private static class PixelComparator implements Comparator<RecoPixel> {

        @Override
        public int compare(RecoPixel recoPixel, RecoPixel t1) {
            return t1.val - recoPixel.val;
        }
    }
    void prunePixels(ArrayList<RecoPixel> pixels, int N) {
        if (pixels.size() <= N) { return; }
        //Collections.sort(pixels, PIXEL_COMPARATOR);
        pixels.subList(N, pixels.size()).clear();
    }

    @Override
    ArrayList<RecoPixel> buildPixels() {
        ArrayList<RecoPixel> pixels = new ArrayList<>();

        int width = mFrame.getSize().width;
        int height = mFrame.getSize().height;

        byte[] bytes = mFrame.getBytes();

        ExposureBlock xb = mFrame.getExposureBlock();

        // recalculate the variance w/ full stats
        double variance = 0.;
        double avg = mFrame.getPixAvg();
        boolean fail = false;
        for (int ix = 0; ix < width; ix++) {
            for (int iy = 0; iy < height; iy++) {
                // NB: cast (signed) byte to integer for meaningful comparisons!
                int val = bytes[ix + width * iy] & 0xFF;

                variance += (val-avg)*(val - avg);

                if (val > xb.L2_threshold) {
                    if (fail) {
                        mEvent.npix_dropped++;
                        continue;
                    }
                    // okay, found a pixel above threshold!
                    if (pixels.size() >= mNpix) {
                        if (pixels.size() > 2*mNpix) {
                            prunePixels(pixels, mNpix);
                        }
                        if (val > pixels.get(pixels.size()).val) {
                            continue;
                        }
                    }

                    RecoPixel p;
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

                    // look at the 8 adjacent pixels to measure max and avg values
                    int sum3 = 0, sum5 = 0;
                    int norm3 = 0, norm5 = 0;
                    int nearMax = 0, nearMax5 = 0;
                    for (int dx = -2; dx <= 2; ++dx) {
                        for (int dy = -2; dy <= 2; ++dy) {
                            if (dx == 0 && dy == 0) {
                                // exclude center from average
                                continue;
                            }

                            int idx = ix + dx;
                            int idy = iy + dy;
                            if (idx < 0 || idy < 0 || idx >= width || idy >= height) {
                                // we're off the sensor plane.
                                continue;
                            }

                            int dval = bytes[idx + width * idy] & 0xFF;
                            sum5 += dval;
                            norm5++;
                            nearMax5 = Math.max(nearMax5, dval);
                            if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                                // we're in the 3x3 part
                                sum3 += dval;
                                norm3++;
                                nearMax = Math.max(nearMax, dval);
                            }
                        }
                    }

                    p.avg_3 = ((float) sum3) / norm3;
                    p.avg_5 = ((float) sum5) / norm5;
                    p.near_max = nearMax;

                    pixels.add(p);
                    Collections.sort(pixels, PIXEL_COMPARATOR);
                }
            }
        }

        prunePixels(pixels, mNpix);

        // update event with the "variance" computed from full pixel statistics.
        variance /= (double)(width*height);
        variance = Math.sqrt(variance);
        mEvent.variance = variance;

        return pixels;
    }
}
