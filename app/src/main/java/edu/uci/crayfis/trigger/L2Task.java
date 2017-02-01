package edu.uci.crayfis.trigger;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/12/16.
 */
public class L2Task implements Runnable {

    private static final CFConfig CONFIG = CFConfig.getInstance();
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

    RawCameraFrame mFrame = null;
    L2Processor mL2Processor = null;

    RecoEvent mEvent = null;

    final L2Config mConfig;

    L2Task(L2Processor l2processor, RawCameraFrame frame, L2Config config) {
        mFrame = frame;
        mL2Processor = l2processor;

        mApplication = mL2Processor.mApplication;

        mConfig = config;
    }

    public static class RecoEvent implements Parcelable {
        public long time;
        public long time_nano;
        public long time_ntp;
        public Location location;
        public float[] orientation;

        public boolean quality;
        public double background;
        public double variance;

        public int npix_dropped;

        public int xbn;

        public ArrayList<RecoPixel> pixels = new ArrayList<RecoPixel>();

        public RecoEvent() {

        }

        private RecoEvent(@NonNull final Parcel parcel) {
            time = parcel.readLong();
            time_nano = parcel.readLong();
            time_ntp = parcel.readLong();
            location = parcel.readParcelable(Location.class.getClassLoader());
            orientation = parcel.createFloatArray();
            quality = parcel.readInt() == 1;
            background = parcel.readDouble();
            variance = parcel.readDouble();
            npix_dropped = parcel.readInt();
            xbn = parcel.readInt();
            pixels = parcel.createTypedArrayList(RecoPixel.CREATOR);
        }

        public DataProtos.Event buildProto() {
            DataProtos.Event.Builder buf = DataProtos.Event.newBuilder();

            buf.setTimestamp(time);
            buf.setTimestampNano(time_nano);
            buf.setTimestampNtp(time_ntp);
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
            buf.setStd(variance);

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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeLong(time);
            dest.writeLong(time_nano);
            dest.writeLong(time_ntp);
            dest.writeParcelable(location, flags);
            dest.writeFloatArray(orientation);
            dest.writeInt(quality ? 1 : 0);
            dest.writeDouble(background);
            dest.writeDouble(variance);
            dest.writeInt(npix_dropped);
            dest.writeInt(xbn);
            dest.writeTypedList(pixels);
        }

        public static final Creator<RecoEvent> CREATOR = new Creator<RecoEvent>() {
            @Override
            public RecoEvent createFromParcel(final Parcel source) {
                return new RecoEvent(source);
            }

            @Override
            public RecoEvent[] newArray(final int size) {
                return new RecoEvent[size];
            }
        };
    }

    public static class RecoPixel implements Parcelable {
        public int x, y;
        public int val;
        public float avg_3, avg_5;
        public int near_max;

        public DataProtos.Pixel buildProto() {
            DataProtos.Pixel.Builder buf = DataProtos.Pixel.newBuilder();
            buf.setX(x);
            buf.setY(y);
            buf.setVal(val);
            buf.setAvg3(avg_3);
            buf.setAvg5(avg_5);
            buf.setNearMax(near_max);

            return buf.build();
        }

        public RecoPixel() {

        }

        private RecoPixel(@NonNull final Parcel parcel) {
            x = parcel.readInt();
            y = parcel.readInt();
            val  = parcel.readInt();
            avg_3 = parcel.readFloat();
            avg_5 = parcel.readFloat();
            near_max = parcel.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeInt(x);
            dest.writeInt(y);
            dest.writeInt(val);
            dest.writeFloat(avg_3);
            dest.writeFloat(avg_5);
            dest.writeInt(near_max);
        }

        /**
         * Parcelable.
         */
        public static final Creator<RecoPixel> CREATOR = new Creator<RecoPixel>() {
            @Override
            public RecoPixel createFromParcel(final Parcel source) {
                return new RecoPixel(source);
            }

            @Override
            public RecoPixel[] newArray(final int size) {
                return new RecoPixel[size];
            }
        };
    }

    protected RecoEvent buildEvent() {
        RecoEvent event = new RecoEvent();

        event.time = mFrame.getAcquiredTime();
        event.time_nano = mFrame.getAcquiredTimeNano();
        event.time_ntp = mFrame.getAcquiredTimeNTP();
        event.location = mFrame.getLocation();
        event.orientation = mFrame.getOrientation();

        // first we measure the background and variance, but to save time only do it for every
        // stepW or stepH-th pixel
        final int width = mFrame.getSize().width;
        final int height = mFrame.getSize().height;

        // When measuring bg and variance, we only look at some pixels
        // to save time. stepW and stepH are the number of pixels skipped
        // between samples.
        final int stepW = 10;
        final int stepH = 10;

        final byte[] bytes = mFrame.getBytes();

        // calculate variance of the background
        double variance = 0;
        double avg = mFrame.getPixAvg();
        int npixels = 0;
        for (int ix=mFrame.BORDER;ix < width-mFrame.BORDER;ix+= stepW)
            for (int iy=0;iy<height;iy+=stepH)
            {
                int val = bytes[ix+width*iy]&0xFF;
                variance += (val-avg)*(val - avg);
                npixels++;
            }
        if (npixels>0) {
            variance = Math.sqrt(variance / ((double) npixels));
        }

        // is the data good?
        // TODO: investigate what makes sense here!
        boolean good_quality = (avg < CONFIG.getQualityBgAverage() && variance < CONFIG.getQualityBgVariance()); // && percent_hit < max_pix_frac);

        event.background = avg;
        event.variance = variance;
        event.quality = good_quality;

        if (!event.quality) {
            CFLog.w("Got bad quality event. avg req: " + CONFIG.getQualityBgAverage() + ", obs: " + avg);
            CFLog.w("var req: " + CONFIG.getQualityBgVariance() + ", obs: " + variance);
        }

        return event;
    }

    ArrayList<RecoPixel> buildPixels() {
        ArrayList<RecoPixel> pixels = new ArrayList<>();

        Mat greyMat = mFrame.getGreyMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = greyMat.width();
        int height = greyMat.height();


        ExposureBlock xb = mFrame.getExposureBlock();

        // set everything below threshold to zero
        Imgproc.threshold(greyMat, threshMat, xb.L2_threshold-1, 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);

        for(int i=0; i<l2PixelCoords.total(); i++) {
            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = (int) greyMat.get(iy, ix)[0];
            CFLog.d("x:" + ix);
            CFLog.d("y:" + iy);
            CFLog.d("val:" + val);
            CFLog.d("thresh:" + xb.L2_threshold);

            RecoPixel p;

            L2Processor.histL2Pixels.fill(val);
            try {
                p = new RecoPixel();
            } catch (OutOfMemoryError e) {
                CFLog.e("Cannot allocate anymore L2 pixels: out of memory!!!");
                mEvent.npix_dropped++;
                continue;
            }

            // record the coordinates of the frame, not of the sliced Mat we used
            p.x = ix+mFrame.BORDER;
            p.y = iy+mFrame.BORDER;
            p.val = val;

            Mat greyAvg3 = greyMat.submat(Math.max(iy-1,0), Math.min(iy+2,height),
                    Math.max(ix-1,0), Math.min(ix+2,width));
            Mat greyAvg5 = greyMat.submat(Math.max(iy-2,0), Math.min(iy+3,height),
                    Math.max(ix-2,0), Math.min(ix+3,width));

            p.avg_3 = (float)Core.mean(greyAvg3).val[0];
            p.avg_5 = (float)Core.mean(greyAvg5).val[0];
            p.near_max = (int)Core.minMaxLoc(greyAvg3).maxVal;


            pixels.add(p);

        }

        return pixels;
    }

    @Override
    public void run() {
        ++mL2Processor.mL2Count;

        ExposureBlock xb = mFrame.getExposureBlock();
        xb.L2_processed++;

        // First, build the event from the raw frame.
        mEvent = buildEvent();


        if (!mEvent.quality && !CONFIG.getTriggerLock()) {
            // bad data quality detected! kill this event and throw it into stabilization mode
            // (if it's not already)

            // TODO: A better way to handle this would be to flag the xb so that any
            // future frames from the same XB be dropped.
            CFLog.d(" !! BAD DATA! quality = " + mEvent.quality);
            if (mApplication.getApplicationState() != CFApplication.State.STABILIZATION) {
                mApplication.setApplicationState(CFApplication.State.STABILIZATION);
            }
            return;
        }

        // If this event was not taken in DATA mode, we're done here.
        if (xb.daq_state != CFApplication.State.DATA) {
            return;
        }

        // now it's time to do the pixel-level analysis
        ArrayList<RecoPixel> pixels = buildPixels();

        mEvent.pixels = pixels;

        // TODO: some more stuff here for visualization?

        // Finally, add the event to the proper exposure block
        xb.addEvent(mEvent);
        // And notify the XB that we are done processing this frame.
        mFrame.clear();

        // TODO: inspect pixels for "image saving" feature
    }
}
