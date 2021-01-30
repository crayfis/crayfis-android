package io.crayfis.android.daq;

/**
 * Created by cshimmin on 5/13/16.
 */
public class AcquisitionTime {
    public final long Nano;
    public final long NTP;
    public final long Sys;
    public AcquisitionTime() {
        // fetch timestamps in order of decreasing precision
        Nano = System.nanoTime();
        NTP = SntpClient.getInstance().getNtpTime();
        Sys = System.currentTimeMillis();
    }
}