package io.crayfis.android.calibration;

import io.crayfis.android.server.CFConfig;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.util.CFLog;

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
     *  or equal to the specified value and write to CFConfig
     */
    public void updateThresholds() {

        // first, find the target L1 efficiency
        final CFConfig CONFIG = CFConfig.getInstance();
        double fps = CFCamera.getInstance().getFPS();

        if (fps == 0) {
            CFLog.w("Warning! Got 0 fps in threshold calculation.");
        }
        double targetL1Rate = CONFIG.getTargetEventsPerMinute() / 60.0 / fps;

        Histogram h = maxPixels.getHistogram();
        long[] histValues = h.getValues();
        long nTotal = h.getEntries();
        int nTarget = (int) (nTotal * targetL1Rate);

        int thresh;

        for (thresh = 0; thresh < 256; thresh++) {
            nTotal -= histValues[thresh];
            //if (thresh<20) CFLog.d(" L1Calibrator. Thresh="+thresh+" integral="+h.getIntegral(thresh, 256)+" rate="+rate+" compare to "+target_eff);
            if (nTotal < nTarget) {
                break;
            }
        }

        CFLog.i("Setting new L1 threshold: {" + CONFIG.getL1Threshold() + "} -> {" + thresh + "}");

        CONFIG.setL1Threshold(thresh);
        if (thresh > 2) {
            CONFIG.setL2Threshold(thresh - 1);
        } else {
            // Okay, if we're getting this low, we shouldn't try to
            // set the L2thresh any lower, else event frames will be huge.
            CONFIG.setL2Threshold(thresh);
        }
    }

    public void resize(int n) {
        n_frames = n;
        maxPixels.resize(n);
    }

    public void destroy() {
        sInstance = null;
    }

}
