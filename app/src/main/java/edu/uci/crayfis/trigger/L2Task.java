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

    private RecoEvent buildEvent() {
        RecoEvent event = new RecoEvent();

        event.time = mFrame.getAcquiredTime();
        event.time_nano = mFrame.getAcquiredTimeNano();
        event.time_ntp = mFrame.getAcquiredTimeNTP();
        event.location = mFrame.getLocation();
        event.orientation = mFrame.getOrientation();

        // get mean background level
        double background = 0;
        double variance = 0;

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

        // TODO: see if we can do this more efficiently
        // (there is a one-pass algorithm but it may not be stable)

        // calculate mean background value
        for (int ix = 0; ix < width; ix += stepW) {
            for (int iy = 0; iy < height; iy+=stepH) {
                int val = bytes[ix+width*iy]&0xFF;
                background += (float)val;
                npixels++;
            }
        }
        if (npixels > 0) {
            background /= (double) npixels;
        }

        // calculate variance
        for (int ix=0;ix < width;ix+= stepW)
            for (int iy=0;iy<height;iy+=stepH)
            {
                int val = bytes[ix+width*iy]&0xFF;
                variance += (val-background)*(val - background);
            }
        if (npixels>0) {
            variance = Math.sqrt(variance / ((double) npixels));
        }

        // is the data good?
        // TODO: investigate what makes sense here!
        boolean good_quality = (background < CONFIG.getQualityBgAverage() && variance < CONFIG.getQualityBgVariance()); // && percent_hit < max_pix_frac);

        event.background = background;
        event.variance = variance;
        event.quality = good_quality;

        return event;
    }

    ArrayList<RecoPixel> buildPixels() {
        // TODO
        return null;
    }

    @Override
    public void run() {
        ++mL2Processor.mL2Count;

        ExposureBlock xb = mFrame.getExposureBlock();
        xb.L2_processed++;

        // First, build the event from the raw frame.
        //ParticleReco.RecoEvent event = PARTICLE_RECO.buildEvent(mFrame);
        RecoEvent event = buildEvent();


        if (!event.quality && !CONFIG.getTriggerLock()) {
            // bad data quality detected! kill this event and throw it into stabilization mode
            // (if it's not already)

            // TODO: A better way to handle this would be to flag the xb so that any
            // future frames from the same XB be dropped.
            CFLog.d(" !! BAD DATA! quality = " + event.quality);
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

        event.pixels = pixels;

        // TODO: some more stuff here

        // Finally, add the event to the proper exposure block
        xb.addEvent(event);

        // TODO: inspect pixels for "image saving" feature
    }
}
