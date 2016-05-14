package edu.uci.crayfis.trigger;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

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

    RawCameraFrame mFrame = null;
    L2Processor mL2Processor = null;

    RecoEvent mEvent = null;

    L2Task(RawCameraFrame frame, L2Processor l2processor) {
        mFrame = frame;
        mL2Processor = l2processor;

        mApplication = mL2Processor.mApplication;
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

        int npixels = 0;

        final byte[] bytes = mFrame.getBytes();

        // calculate variance of the background
        double variance = 0;
        double avg = mFrame.getPixAvg();
        for (int ix=0;ix < width;ix+= stepW)
            for (int iy=0;iy<height;iy+=stepH)
            {
                int val = bytes[ix+width*iy]&0xFF;
                variance += (val-avg)*(val - avg);
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

        return event;
    }

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

                if (val > xb.L2_thresh) {
                    if (fail) {
                        mEvent.npix_dropped++;
                        continue;
                    }
                    // okay, found a pixel above threshold!
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
                }
            }
        }

        // update event with the "variance" computed from full pixel statistics.
        variance /= (double)(width*height);
        variance = Math.sqrt(variance);
        mEvent.variance = variance;

        return pixels;
    }

    @Override
    public void run() {
        ++mL2Processor.mL2Count;

        ExposureBlock xb = mFrame.getExposureBlock();
        xb.L2_processed++;

        // First, build the event from the raw frame.
        //ParticleReco.RecoEvent event = PARTICLE_RECO.buildEvent(mFrame);
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

        // TODO: inspect pixels for "image saving" feature
    }
}
