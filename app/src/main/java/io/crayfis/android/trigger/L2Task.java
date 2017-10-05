package io.crayfis.android.trigger;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

import io.crayfis.android.CFApplication;
import io.crayfis.android.DataProtos;
import io.crayfis.android.R;
import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.gallery.SavedImage;
import io.crayfis.android.gallery.Utils;
import io.crayfis.android.ui.LayoutBlack;
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

    RawCameraFrame mFrame = null;
    L2Processor mL2Processor = null;

    RecoEvent mEvent = null;

    final Config mConfig;

    final Utils mUtils;

    L2Task(L2Processor l2processor, RawCameraFrame frame, Config config) {
        mFrame = frame;
        mL2Processor = l2processor;

        mApplication = mL2Processor.mApplication;

        mConfig = config;

        mUtils = new Utils(mApplication);
    }

    public static class RecoEvent implements Parcelable {
        public long time;
        public long time_nano;
        public long time_ntp;
        public long time_target;
        public int batteryTemp;
        public Location location;
        public float[] orientation;
        public float pressure;

        public boolean quality;
        public double background;
        public double std;

        public int npix_dropped;

        public int xbn;

        public ArrayList<RecoPixel> pixels = new ArrayList<RecoPixel>();

        public RecoEvent() {

        }

        private RecoEvent(@NonNull final Parcel parcel) {
            time = parcel.readLong();
            time_nano = parcel.readLong();
            time_ntp = parcel.readLong();
            time_target = parcel.readLong();
            batteryTemp = parcel.readInt();
            location = parcel.readParcelable(Location.class.getClassLoader());
            orientation = parcel.createFloatArray();
            pressure = parcel.readFloat();
            quality = parcel.readInt() == 1;
            background = parcel.readDouble();
            std = parcel.readDouble();
            npix_dropped = parcel.readInt();
            xbn = parcel.readInt();
            pixels = parcel.createTypedArrayList(RecoPixel.CREATOR);
        }

        public DataProtos.Event buildProto() {
            DataProtos.Event.Builder buf = DataProtos.Event.newBuilder();

            buf.setTimestamp(time);
            buf.setTimestampNano(time_nano);
            buf.setTimestampNtp(time_ntp);
            buf.setTimestampTarget(time_target);
            buf.setPressure(pressure);
            buf.setBatteryTemp(batteryTemp);
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeLong(time);
            dest.writeLong(time_nano);
            dest.writeLong(time_ntp);
            dest.writeLong(time_target);
            dest.writeInt(batteryTemp);
            dest.writeParcelable(location, flags);
            dest.writeFloatArray(orientation);
            dest.writeFloat(pressure);
            dest.writeInt(quality ? 1 : 0);
            dest.writeDouble(background);
            dest.writeDouble(std);
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
        public int val, adjusted_val;
        public float avg_3, avg_5;
        public int near_max;

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

        public RecoPixel() {

        }

        private RecoPixel(@NonNull final Parcel parcel) {
            x = parcel.readInt();
            y = parcel.readInt();
            val  = parcel.readInt();
            adjusted_val = parcel.readInt();
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
            dest.writeInt(adjusted_val);
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
        event.time_target = mFrame.getTimestamp();
        event.location = mFrame.getLocation();
        event.orientation = mFrame.getOrientation();
        event.pressure = mFrame.getPressure();
        event.batteryTemp = mFrame.getBatteryTemp();

        double avg = mFrame.getPixAvg();
        double std = mFrame.getPixStd();

        event.background = avg;
        event.std = std;

        LayoutBlack lb = LayoutBlack.getInstance();
        if(lb.events != null) {
            lb.addEvent(event);
        }

        return event;
    }

    ArrayList<RecoPixel> buildPixels() {
        ArrayList<RecoPixel> pixels = new ArrayList<>();

        Mat grayMat = mFrame.getGrayMat();
        Mat threshMat = new Mat();
        Mat l2PixelCoords = new Mat();

        int width = grayMat.width();
        int height = grayMat.height();


        ExposureBlock xb = mFrame.getExposureBlock();

        // set everything below threshold to zero
        Imgproc.threshold(grayMat, threshMat, xb.L2_threshold, 0, Imgproc.THRESH_TOZERO);
        Core.findNonZero(threshMat, l2PixelCoords);
        threshMat.release();

        long pixN = Math.min(l2PixelCoords.total(), mConfig.npix);

        for(int i=0; i<pixN; i++) {

            double[] xy = l2PixelCoords.get(i,0);
            int ix = (int) xy[0];
            int iy = (int) xy[1];
            int val = mFrame.getRawByteAt(ix, iy) & 0xFF;
            int adjustedVal = (int) grayMat.get(iy, ix)[0];
            CFLog.d("val = " + val + ", adjusted = " + adjustedVal + " at (" + ix + "," + iy +")");

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
        mEvent = buildEvent();

        // If this event was not taken in DATA mode, we're done here.
        if (xb.daq_state != CFApplication.State.DATA) {
            return;
        }

        // now it's time to do the pixel-level analysis
        ArrayList<RecoPixel> pixels = buildPixels();

        mEvent.pixels = pixels;

        if(pixels.size() >= 7) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mApplication);
            if(sharedPrefs.getBoolean(mApplication.getString(R.string.prefEnableGallery), false)
                    && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || mApplication.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED)) {

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
