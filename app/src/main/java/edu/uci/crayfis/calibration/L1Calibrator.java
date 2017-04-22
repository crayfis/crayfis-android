package edu.uci.crayfis.calibration;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

public class L1Calibrator {
    private final FrameHistogram maxPixels;

    private int n_frames = 1000;

    private L1Calibrator() {
        maxPixels = new FrameHistogram(n_frames);
    }

    private static L1Calibrator sInstance;

    public static synchronized L1Calibrator getInstance() {
        if (sInstance == null) {
            sInstance = new L1Calibrator();
        }
        return sInstance;
    }

    public FrameHistory<Integer> getMaxPixels() { return maxPixels; }

    public void clear() {
        synchronized (maxPixels) {
            maxPixels.clear();
        }
    }

    public Histogram getHistogram() { return maxPixels.getHistogram(); }

    public void addFrame(RawCameraFrame frame) {
        int frameMax = frame.getPixMax();
        synchronized (maxPixels) {
            maxPixels.addValue(frameMax);
        }
    }

    /**
     *  Find an integer L1 threshold s.t. the average L1 rate is less than
     *  or equal to the specified value.
     *  @param target_eff The target (maximum) fraction of events passing L1
     */
    public int findL1Threshold(double target_eff) {
        Histogram h = maxPixels.getHistogram();
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

    public void resize(int n) {
        n_frames = n;
        maxPixels.resize(n);
    }

}
