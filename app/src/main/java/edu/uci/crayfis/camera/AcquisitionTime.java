package edu.uci.crayfis.camera;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import edu.uci.crayfis.CFApplication;

/**
 * Created by cshimmin on 5/13/16.
 */
public class AcquisitionTime implements Parcelable {
    public final long Nano;
    public final long NTP;
    public final long Sys;
    public AcquisitionTime() {
        // fetch timestamps in order of decreasing precision
        Nano = System.nanoTime() - CFApplication.getStartTimeNano();
        NTP = SntpClient.getInstance().getNtpTime();
        Sys = System.currentTimeMillis();
    }

    private AcquisitionTime(@NonNull final Parcel parcel) {
        Nano = parcel.readLong();
        NTP = parcel.readLong();
        Sys = parcel.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(Nano);
        parcel.writeLong(NTP);
        parcel.writeLong(Sys);
    }

    public static final Creator<AcquisitionTime> CREATOR = new Creator<AcquisitionTime>() {
        @Override
        public AcquisitionTime createFromParcel(final Parcel source) {
            return new AcquisitionTime(source);
        }

        @Override
        public AcquisitionTime[] newArray(final int size) {
            return new AcquisitionTime[size];
        }
    };
}