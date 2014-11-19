package edu.uci.crayfis.calibration;

import edu.uci.crayfis.camera.RawCameraFrame;

public class L1Calibrator {
    private FrameHistory<Integer> max_pixels;

    public L1Calibrator(int n_frames) {
        max_pixels = new FrameHistory<Integer>(n_frames);
    }

    public void clear() {
        max_pixels.clear();
    }

    public void AddFrame(RawCameraFrame frame) {
        max_pixels.add_value(frame.getPixMax());
    }

    public Histogram getHistogram() {
        return max_pixels.getHistogram(256);
    }

    /**
     *  Find an integer L1 threshold s.t. the average L1 rate is less than
     *  or equal to the specified value.
     *  @param target_eff The target (maximum) fraction of events passing L1
     */
    public int findL1Threshold(double target_eff) {
        Histogram h = getHistogram();
        int n_total = h.getEntries();

        int thresh;
        double rate = 0;
        for (thresh = 255; thresh >= 1; --thresh) {
            rate = h.getIntegral(thresh, 256) / n_total;
            if (rate > target_eff) break;
        }
        if (rate > target_eff) {
            return thresh + 1;
        } else {
            return thresh;
        }
    }
}
