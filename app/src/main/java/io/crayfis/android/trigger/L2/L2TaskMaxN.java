package io.crayfis.android.trigger.L2;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.ui.navdrawer.data.LayoutData;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by cshimmin on 5/16/16.
 */
class L2TaskMaxN extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {
        static final int DEFAULT_NPIX = 25;
        static final int DEFAULT_THRESH = 255;

        final int thresh;
        final int npix;
        Config(String name, String cfg) {
            super(name, cfg);

            thresh = CFUtil.getInt(mTaskConfig.get("thresh"), DEFAULT_THRESH);
            npix = CFUtil.getInt(mTaskConfig.get("npix"), DEFAULT_NPIX);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new L2TaskMaxN(processor, frame, this);
        }
    }

    private static final PixelComparator PIXEL_COMPARATOR = new PixelComparator();

    private final Config mConfig;

    private L2TaskMaxN(TriggerProcessor processor, RawCameraFrame frame, Config cfg) {
        super(processor, frame);
        mConfig = cfg;
    }

    private static class PixelComparator implements Comparator<DataProtos.Pixel> {

        @Override
        public int compare(DataProtos.Pixel p0, DataProtos.Pixel p1) {
            return p1.getAdjustedVal() - p0.getAdjustedVal();
        }
    }
    private void prunePixels(ArrayList<DataProtos.Pixel> pixels, int N) {
        if (pixels.size() <= N) return;
        Collections.sort(pixels, PIXEL_COMPARATOR);
        pixels.subList(N, pixels.size()).clear();
    }

    @Override
    public boolean processFrame(RawCameraFrame frame) {

        L2Processor.L2Count++;

        ArrayList<DataProtos.Pixel> pixels = new ArrayList<>();

        Mat grayMat = frame.getGrayMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();

        Imgproc.threshold(grayMat, threshMat, mConfig.thresh, 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);
        threshMat.release();

        for(int i=0; i<l2PixelCoords.total(); i++) {

            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = frame.getRawByteAt(ix, iy) & 0xFF;
            int adjustedVal = (int) grayMat.get(iy, ix)[0];

            LayoutData.appendData(val);
            try {
                DataProtos.Pixel.Builder pixBuilder = DataProtos.Pixel.newBuilder();
                Mat grayAvg3 = grayMat.submat(Math.max(iy-1,0), Math.min(iy+2,height),
                        Math.max(ix-1,0), Math.min(ix+2,width));
                Mat grayAvg5 = grayMat.submat(Math.max(iy-2,0), Math.min(iy+3,height),
                        Math.max(ix-2,0), Math.min(ix+3,width));

                pixBuilder.setX(ix)
                        .setY(iy)
                        .setVal(val)
                        .setAdjustedVal(adjustedVal)
                        .setAvg3((float)Core.mean(grayAvg3).val[0])
                        .setAvg5((float)Core.mean(grayAvg5).val[0])
                        .setAvg5((int)Core.minMaxLoc(grayAvg3).maxVal);

                grayAvg3.release();
                grayAvg5.release();

                pixels.add(pixBuilder.build());
                prunePixels(pixels, mConfig.npix);

            } catch (OutOfMemoryError e) {
                CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
            }

        }

        l2PixelCoords.release();
        mProcessor.pass += pixels.size();
        frame.setPixels(pixels);

        return true;
    }
}
