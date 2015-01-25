package edu.uci.crayfis.particle;

/******************************************
 *
 *  class to extract particle candidates from integrated camera frames
 *
 */

import android.hardware.Camera;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.ArrayList;

import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.calibration.Histogram;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

public class ParticleReco {
    private static final CFConfig CONFIG = CFConfig.getInstance();

    public boolean good_quality;

    public long time;
    public Location location;

    //public static float default_max_pix_frac = (float)0.15;
    //private float max_pix_frac;

    private static ParticleReco sInstance;

    /**
     * Get the instance of {@link edu.uci.crayfis.particle.ParticleReco}.
     *
     * @return {@link edu.uci.crayfis.particle.ParticleReco}
     */
    public static synchronized ParticleReco getInstance() {
        if (sInstance == null) {
            sInstance = new ParticleReco();
        }
        return sInstance;
    }

    public static synchronized void setPreviewSize(@NonNull final Camera.Size inPreviewSize) {
        ParticleReco.previewSize = inPreviewSize;
    }

    private ParticleReco() {
        good_quality = false;
        clearHistograms();
    }

    public int getTotalPixels() {
        return previewSize.height * previewSize.width;
    }

    public static class RecoEvent implements Parcelable {
        public long time;
        public Location location;
        public float[] orientation;

        public boolean quality;
        public float background;
        public float variance;

        public int xbn;

        public ArrayList<RecoPixel> pixels = new ArrayList<RecoPixel>();

        public RecoEvent() {

        }

        private RecoEvent(@NonNull final Parcel parcel) {
            time = parcel.readLong();
            location = parcel.readParcelable(Location.class.getClassLoader());
            orientation = parcel.createFloatArray();
            quality = parcel.readInt() == 1;
            background = parcel.readFloat();
            variance = parcel.readFloat();
            xbn = parcel.readInt();
        }

        public DataProtos.Event buildProto() {
            DataProtos.Event.Builder buf = DataProtos.Event.newBuilder();

            buf.setTimestamp(time);
            buf.setGpsLat(location.getLatitude());
            buf.setGpsLon(location.getLongitude());
            buf.setOrientX(orientation[0]);
            buf.setOrientY(orientation[1]);
            buf.setOrientZ(orientation[2]);

            buf.setAvg(background);
            buf.setStd(variance);

            buf.setXbn(xbn);

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
            dest.writeParcelable(location, flags);
            dest.writeFloatArray(orientation);
            dest.writeInt(quality ? 1 : 0);
            dest.writeFloat(background);
            dest.writeFloat(variance);
            dest.writeInt(xbn);
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

    public class History {
        public int[] values;
        final int nbins;
        public int current_time = 0;

        public History(int nbins) {
            if (nbins <= 0) {
                throw new RuntimeException("Hey dumbo a histogram should have at least one bin.");

            }
            this.nbins = nbins;

            values = new int[nbins];

        }

        public void clear() {
            for (int i = 0; i < values.length; ++i) {
                values[i] = 0;
            }
            current_time=0;
        }

        public void new_data(int data)
        {
            current_time++;
            if (current_time>=nbins) current_time=0;
            values[current_time] = data;
        }

    }

    public Histogram h_pixel = new Histogram(256);
    public Histogram h_l2pixel = new Histogram(256);
    public Histogram h_maxpixel = new Histogram(256);
    public Histogram h_numpixel = new Histogram(200);


    public History hist_mean = new History(200);

    // counters to keep track of how many events and pixels
    // have been built since the last reset.
    public int event_count = 0;
    public int pixel_count = 0;

    // reset the state of the ParticleReco object;
    // that means clearing histograms and counters
    public void reset() {
        clearHistograms();
        event_count = 0;
        pixel_count = 0;
    }

    public void clearHistograms() {

        h_pixel.clear();
        h_l2pixel.clear();
        h_maxpixel.clear();
        h_numpixel.clear();
        hist_mean.clear();
    }

    // When measuring bg and variance, we only look at some pixels
    // to save time. stepW and stepH are the number of pixels skipped
    // between samples.
    public static final int stepW = 10;
    public static final int stepH = 10;

    public static Camera.Size previewSize = null;

    public RecoEvent buildEvent(RawCameraFrame frame) {
        event_count++;

        RecoEvent event = new RecoEvent();

        event.time = frame.getAcquiredTime();
        event.location = frame.getLocation();
        event.orientation = frame.getOrientation();

        // first we measure the background and variance, but to save time only do it for every
        // stepW or stepH-th pixel

        // get mean background level
        float background = 0;
        float variance = 0;

        float percent_hit = 0;

        int width = previewSize.width;
        int height = previewSize.height;

        int npixels = 0;
        int npixels_hit = 0;

        byte[] bytes = frame.getBytes();

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
            background = (float)background/((float)1.0*npixels);
        }

        // calculate variance
        for (int ix=0;ix < width;ix+= stepW)
            for (int iy=0;iy<height;iy+=stepH)
            {
                int val = bytes[ix+width*iy]&0xFF;
                variance += (val-background)*(val - background);
            }
        if (npixels>0)
            variance = (float)Math.sqrt((float)variance/((float)1.0*npixels));

        /** this is not really an image quality check, do this in DAQActivity
         // Reject an image if too many pixels were hit
         // Good for security and one more check for image quality

         // Get total number of pixels of frame
         float tot_pix = (float)width*(float)height;

         // Find the number of pixels hit in frame
         for (int ix = 0; ix < width; ix++) {
         for (int iy = 0; iy < height; iy++) {
         int val = bytes[ix+width*iy]&0xFF;
         if (val >= 10) {
         npixels_hit++;
         }
         }
         }

         // Find percent hit
         percent_hit = (float)npixels_hit/tot_pix;
         */
        // is the data good?
        // TODO: investigate what makes sense here!
        good_quality = (background < CONFIG.getQualityBgAverage() && variance < CONFIG.getQualityBgVariance()); // && percent_hit < max_pix_frac);

        //CFLog.d("reco: background = "+background+" var = "+variance+" %hit = "+percent_hit+" qual = "+good_quality);

        event.background = background;
        event.variance = variance;
        event.quality = good_quality;

        return event;
    }

    public ArrayList<RecoPixel> buildL2Pixels(RawCameraFrame frame, int thresh) {
        return buildL2Pixels(frame, thresh, false);
    }

    public ArrayList<RecoPixel> buildL2PixelsQuick(RawCameraFrame frame, int thresh) {
        return buildL2Pixels(frame, thresh, true);
    }

    public ArrayList<RecoPixel> buildL2Pixels(RawCameraFrame frame, int thresh, boolean quick) {
        ArrayList<RecoPixel> pixels = null;

        try {

            if (!quick) {
                pixels = new ArrayList<RecoPixel>();
            }


            int width = previewSize.width;
            int height = previewSize.height;
            int max_val = 0;
            int num_pix = 0;
            int tot_pix=0;
            int num_tot_pix=0;

            byte[] bytes = frame.getBytes();

            for (int ix = 0; ix < width; ix++) {
                for (int iy = 0; iy < height; iy++) {
                    // NB: cast (signed) byte to integer for meaningful comparisons!
                    int val = bytes[ix + width * iy] & 0xFF;

                    h_pixel.fill(val);

                    if (val > max_val) {
                        max_val = val;
                    }

                    tot_pix += val;
                    num_tot_pix++;

                    if (val > thresh) {
                        if (!quick) h_l2pixel.fill(val);
                        num_pix++;

                        if (quick) {
                            // okay, bail out without any further reco
                            continue;
                        }

                        // okay, found a pixel above threshold!
                        RecoPixel p = new RecoPixel();
                        p.x = ix;
                        p.y = iy;
                        p.val = val;

                        // look at the 8 adjacent pixels to measure max and ave values
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

            // update the event-level histograms.

            h_maxpixel.fill(max_val);
            h_numpixel.fill(num_pix);

            hist_mean.new_data((int)(tot_pix/(1.0*num_tot_pix)));

            pixel_count += num_pix;
        } catch (OutOfMemoryError e) {
            CFLog.e("Cannot allocate L2 pixels: out of memory!!!");
            return null;
        }
        return pixels;
    }

    public int calculateThresholdByEvents(int max_count) {
        // return a the lowest threshold that will give fewer than
        // max_count events over the integrated period contained
        // in the histograms.

        CFLog.i("Cacluating threshold! Event histo:"
                        + " 0 - " + h_maxpixel.getBinValue(0) + "\n"
                        + " 1 - " + h_maxpixel.getBinValue(1) + "\n"
                        + " 2 - " + h_maxpixel.getBinValue(2) + "\n"
                        + " 3 - " + h_maxpixel.getBinValue(3) + "\n"
                        + " 4 - " + h_maxpixel.getBinValue(4) + "\n"
        );

        if (h_maxpixel.getIntegral() == 0) {
            // wow! no data, either we didn't expose long enough, or
            // the background is super low. Return 0 threshold.
            return 0;
        }

        int integral = 0;
        int new_thresh;
        for (new_thresh = 255; new_thresh >= 0; --new_thresh) {
            integral += h_maxpixel.getBinValue(new_thresh);
            if (integral > max_count) {
                // oops! we've gone over the limit.
                // bump the threshold back up and break out.
                new_thresh++;
                break;
            }
        }

        // FIXME Jodi - added Math.Max because was getting -1 returned here.
        return Math.max(new_thresh, 1);
    }

    public int calculateThresholdByPixels(int stat_factor) {
        int new_thresh = 255;
        // number of photons above this threshold
        int above_thresh = 0;
        do {
            above_thresh += h_pixel.getBinValue(new_thresh);
            CFLog.d("threshold " + new_thresh
                    + " obs= " + above_thresh + "/" + stat_factor);
            new_thresh--;
        } while (new_thresh > 0 && above_thresh < stat_factor);

        return new_thresh;
    }

}
