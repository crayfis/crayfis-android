package io.crayfis.android.trigger;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.preference.PreferenceManager;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;

import io.crayfis.android.CFApplication;
import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.gallery.SavedImage;
import io.crayfis.android.gallery.Utils;
import io.crayfis.android.ui.LayoutBlack;
import io.crayfis.android.ui.LayoutHist;
import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
public class L2Task implements Runnable {

    private final CFApplication mApplication;

    public static class Config extends L2Config {
        public static final int DEFAULT_NPIX = 500;

        public final int npix;
        Config(String name, String cfg) {
            super(name, cfg);

            // FIXME: there's probably an easier/more generic way to parse simple key-val pairs.
            int cfg_npix = DEFAULT_NPIX;
            for (String c : cfg.split(";")) {
                String[] kv = c.split("=");
                if (kv.length != 2) continue;
                String key = kv[0];
                String value = kv[1];

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
            return new L2Task(l2Processor, frame, this);
        }
    }

    private RawCameraFrame mFrame = null;
    L2Processor mL2Processor = null;

    RecoEvent mEvent = null;

    private final Config mConfig;

    private final Utils mUtils;

    L2Task(L2Processor l2processor, RawCameraFrame frame, Config config) {
        mFrame = frame;
        mL2Processor = l2processor;

        mApplication = mL2Processor.mApplication;

        mConfig = config;

        mUtils = new Utils(mApplication);
    }

    public static class RecoEvent {
        private final long time;
        private final long time_nano;
        private final long time_ntp;
        private final long time_target;
        //private final int batteryTemp;
        private final Location location;
        private final float[] orientation;
        private final float pressure;

        private final double background;
        private final double std;

        int npix_dropped;

        private final int xbn;

        private final ArrayList<RecoPixel> pixels;

        RecoEvent(RawCameraFrame frame, L2Task l2Task) {

            time = frame.getAcquiredTime();
            time_nano = frame.getAcquiredTimeNano();
            time_ntp = frame.getAcquiredTimeNTP();
            time_target = frame.getTimestamp();
            location = frame.getLocation();
            orientation = frame.getOrientation();
            pressure = frame.getPressure();
            //batteryTemp = frame.getBatteryTemp();

            background = frame.getPixAvg();
            std = frame.getPixStd();

            xbn = frame.getExposureBlock().getXBN();

            pixels = l2Task.buildPixels(frame);
        }

        public DataProtos.Event buildProto() {
            DataProtos.Event.Builder buf = DataProtos.Event.newBuilder();

            buf.setTimestamp(time);
            buf.setTimestampNano(time_nano);
            buf.setTimestampNtp(time_ntp);
            buf.setTimestampTarget(time_target);
            buf.setPressure(pressure);
            //buf.setBatteryTemp(batteryTemp);
            buf.setGpsLat(location.getLatitude());
            buf.setGpsLon(location.getLongitude());
            buf.setGpsFixtime(location.getTime());
            if (location.hasAccuracy()) {
                buf.setGpsAccuracy(location.getAccuracy());
            }
            if (location.hasAltitude()) {
                buf.setGpsAltitude(location.getAltitude());
            }

            buf.setOrientX(orientation[0]);
            buf.setOrientY(orientation[1]);
            buf.setOrientZ(orientation[2]);

            buf.setAvg(background);
            buf.setStd(std);

            buf.setXbn(xbn);

            if (pixels != null)
                for (RecoPixel p : pixels) {
                    try {
                        buf.addPixels(p.buildProto());
                    } catch (Exception e) { // do not crash
                    }
                }

            return buf.build();
        }

        public Iterator<RecoPixel> getPixelIterator() {
            return pixels.iterator();
        }

        public long getTime() {
            return time;
        }

        public int getNPix() {
            if(pixels == null) {
                return 0;
            }
            return pixels.size();
        }
    }

    public static class RecoPixel {
        int x, y;
        int val, adjusted_val;
        float avg_3, avg_5;
        int near_max;

        public DataProtos.Pixel buildProto() {
            DataProtos.Pixel.Builder buf = DataProtos.Pixel.newBuilder();
            buf.setX(x);
            buf.setY(y);
            buf.setVal(val);
            buf.setAdjustedVal(adjusted_val);
            buf.setAvg3(avg_3);
            buf.setAvg5(avg_5);
            buf.setNearMax(near_max);

            return buf.build();
        }

        private RecoPixel(int x, int y, int val, int adj, float avg3, float avg5, int nearMax) {
            this.x = x;
            this.y = y;
            this.val = val;
            this.adjusted_val = adj;
            this.avg_3 = avg3;
            this.avg_5 = avg5;
            this.near_max = nearMax;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getVal() {
            return val;
        }

        public static class Builder {
            private int x, y;
            private int val, adjusted_val;
            private float avg_3, avg_5;
            private int near_max;

            public Builder setX(int x) {
                this.x = x;
                return this;
            }

            public Builder setY(int y) {
                this.y = y;
                return this;
            }

            public Builder setVal(int val) {
                this.val = val;
                return this;
            }

            public Builder setAdjustedVal(int adj) {
                this.adjusted_val = adj;
                return this;
            }

            public Builder setAvg3(float avg3) {
                this.avg_3 = avg3;
                return this;
            }

            public Builder setAvg5(float avg5) {
                this.avg_5 = avg5;
                return this;
            }

            public Builder setNearMax(int nearMax) {
                this.near_max = nearMax;
                return this;
            }

            public RecoPixel build() {
                return new RecoPixel(x, y, val, adjusted_val, avg_3, avg_5, near_max);
            }
        }
    }

    ArrayList<RecoPixel> buildPixels(RawCameraFrame frame) {
        ArrayList<RecoPixel> pixels = new ArrayList<>();

        Mat grayMat = frame.getGrayMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();


        ExposureBlock xb = frame.getExposureBlock();

        // set everything below threshold to zero
        Imgproc.threshold(grayMat, threshMat, xb.getL2Thresh(), 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);
        threshMat.release();

        long pixN = Math.min(l2PixelCoords.total(), mConfig.npix);

        for(int i=0; i<pixN; i++) {

            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = frame.getRawByteAt(ix, iy) & 0xFF;
            int adjustedVal = (int) grayMat.get(iy, ix)[0];
            CFLog.d("val = " + val + ", adjusted = " + adjustedVal + " at (" + ix + "," + iy +")");

            LayoutHist.appendData(val);
            try {
                RecoPixel.Builder builder = new RecoPixel.Builder();
                builder.setX(ix)
                        .setY(iy)
                        .setVal(val)
                        .setAdjustedVal(adjustedVal);

                Mat grayAvg3 = grayMat.submat(Math.max(iy-1,0), Math.min(iy+2,height),
                        Math.max(ix-1,0), Math.min(ix+2,width));
                Mat grayAvg5 = grayMat.submat(Math.max(iy-2,0), Math.min(iy+3,height),
                        Math.max(ix-2,0), Math.min(ix+3,width));

                builder.setAvg3((float)Core.mean(grayAvg3).val[0])
                        .setAvg5((float)Core.mean(grayAvg5).val[0])
                        .setAvg5((int)Core.minMaxLoc(grayAvg3).maxVal);

                grayAvg3.release();
                grayAvg5.release();

                pixels.add(builder.build());


            } catch (OutOfMemoryError e) {
                CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
                mEvent.npix_dropped++;
            }

        }
        l2PixelCoords.release();

        return pixels;
    }

    @Override
    public void run() {
        ++L2Processor.mL2Count;

        ExposureBlock xb = mFrame.getExposureBlock();
        xb.L2_processed++;

        // First, build the event from the raw frame.
        mEvent = new RecoEvent(mFrame, this);
        LayoutBlack lb = LayoutBlack.getInstance();
        if(lb.events != null) {
            lb.addEvent(mEvent);
        }

        // If this event was not taken in DATA mode, we're done here.
        if (xb.getDAQState() != CFApplication.State.DATA) {
            return;
        }

        if(mEvent.getNPix() >= 7) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mApplication);
            if(sharedPrefs.getBoolean(mApplication.getString(R.string.prefEnableGallery), false)
                    && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || mApplication.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED)) {

                // make a copy of the pixels
                ArrayList<L2Task.RecoPixel> pixels = new ArrayList<>();
                Iterator<L2Task.RecoPixel> pixIter = mEvent.getPixelIterator();
                while(pixIter.hasNext()) {
                    pixels.add(pixIter.next());
                }

                mUtils.saveImage(new SavedImage(pixels, mFrame.getPixMax(), mFrame.getWidth(),
                        mFrame.getHeight(), mFrame.getAcquiredTimeNano()));
            }
        }

        // Finally, add the event to the proper exposure block
        xb.addEvent(mEvent);
        // And notify the XB that we are done processing this frame.
        mFrame.clear();
    }
}
