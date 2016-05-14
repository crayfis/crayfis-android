package edu.uci.crayfis.calibration;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

public class L1Calibrator {
    private static FrameHistory<Integer> max_pixels;

    private final int n_frames = 1000;

    private L1Calibrator() {
        max_pixels = new FrameHistory<Integer>(n_frames);
    }

    private static L1Calibrator sInstance;

    /**
     * Get the instance of {@link edu.uci.crayfis.particle.ParticleReco}.
     *
     * @return {@link edu.uci.crayfis.particle.ParticleReco}
     */
    public static synchronized L1Calibrator getInstance() {
        if (sInstance == null) {
            sInstance = new L1Calibrator();
        }
        return sInstance;
    }

    public static FrameHistory<Integer> getMaxPixels() { return max_pixels; }

    public static void clear() {
        synchronized (max_pixels) {
            max_pixels.clear();
        }
    }

    public static void AddFrame(RawCameraFrame frame) {
        int frame_max = frame.getPixMax();
        synchronized (max_pixels) {
            max_pixels.add_value(frame_max);
        }
    }

    public static Histogram getHistogram() {
        Histogram h;
        synchronized (max_pixels) {
            h = max_pixels.getHistogram(256);
        }
        return h;
    }

    /**
     *  Find an integer L1 threshold s.t. the average L1 rate is less than
     *  or equal to the specified value.
     *  @param target_eff The target (maximum) fraction of events passing L1
     */
    public static int findL1Threshold(double target_eff) {
        Histogram h = getHistogram();
        int n_total = h.getEntries();

        int thresh;
        double rate = 0;
        for (thresh = 255; thresh >= 1; --thresh) {
            rate = h.getIntegral(thresh, 256) / n_total;
            //if (thresh<20) CFLog.d(" L1Calibrator. Thresh="+thresh+" integral="+h.getIntegral(thresh, 256)+" rate="+rate+" compare to "+target_eff);
            if (rate > target_eff) break;
        }
        if (rate > target_eff) {
            //CFLog.d(" L1Calibrator. Thresh="+(thresh+1));
            return thresh + 1;
        } else {
            //CFLog.d(" L1Calibrator. Thresh="+thresh);
            return thresh;
        }
    }

}
