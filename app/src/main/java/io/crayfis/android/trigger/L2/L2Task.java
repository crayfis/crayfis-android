package io.crayfis.android.trigger.L2;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;

import io.crayfis.android.DataProtos;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.ui.navdrawer.data.LayoutData;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by cshimmin on 5/12/16.
 */
class L2Task extends TriggerProcessor.Task {

    static class Config extends TriggerProcessor.Config {

        static final String NAME = "default";

        static final HashMap<String, Integer> KEY_DEFAULT;

        static {
            KEY_DEFAULT = new HashMap<>();
            KEY_DEFAULT.put("thresh", 255);
            KEY_DEFAULT.put("npix", 120);
        }

        final int thresh;
        final int npix;
        Config(HashMap<String, String> options) {
            super(NAME, options, KEY_DEFAULT);

            thresh = mTaskConfig.get("thresh");
            npix = mTaskConfig.get("npix");
        }

        @Override
        public TriggerProcessor.Config makeNewConfig(String cfgstr) {
            return L2Processor.makeConfig(cfgstr);
        }

        @Override
        public TriggerProcessor.Task makeTask(TriggerProcessor processor, RawCameraFrame frame) {
            return new L2Task(processor, frame, this);
        }
    }

    private final Config mConfig;

    L2Task(TriggerProcessor processor, RawCameraFrame frame, Config cfg) {
        super(processor, frame);
        mConfig = cfg;
    }


    public boolean processFrame(RawCameraFrame frame) {

        L2Processor.L2Count++;

        ArrayList<DataProtos.Pixel> pixels = new ArrayList<>();

        Mat grayMat = frame.getGrayMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();

        // set everything below threshold to zero
        Imgproc.threshold(grayMat, threshMat, mConfig.thresh, 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);
        threshMat.release();

        long pixN = Math.min(l2PixelCoords.total(), mConfig.npix);
        mProcessor.pass += pixN;

        for(int i=0; i<pixN; i++) {

            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = frame.getRawByteAt(ix, iy) & 0xFF;
            int adjustedVal = (int) grayMat.get(iy, ix)[0];
            CFLog.d("val = " + val + ", adjusted = " + adjustedVal + " at (" + ix + "," + iy +")");

            LayoutData.appendData(val);
            try {

                Mat grayAvg3 = grayMat.submat(Math.max(iy-1,0), Math.min(iy+2,height),
                        Math.max(ix-1,0), Math.min(ix+2,width));
                Mat grayAvg5 = grayMat.submat(Math.max(iy-2,0), Math.min(iy+3,height),
                        Math.max(ix-2,0), Math.min(ix+3,width));

                DataProtos.Pixel.Builder pixBuilder = DataProtos.Pixel.newBuilder();

                pixBuilder.setX(ix)
                        .setY(iy)
                        .setVal(val)
                        .setAdjustedVal(adjustedVal)
                        .setAvg3((float)Core.mean(grayAvg3).val[0])
                        .setAvg5((float)Core.mean(grayAvg5).val[0])
                        .setNearMax((int)Core.minMaxLoc(grayAvg3).maxVal);

                grayAvg3.release();
                grayAvg5.release();

                pixels.add(pixBuilder.build());


            } catch (OutOfMemoryError e) {
                CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
            }

        }
        l2PixelCoords.release();

        frame.setPixels(pixels);

        return true;
    }

}
