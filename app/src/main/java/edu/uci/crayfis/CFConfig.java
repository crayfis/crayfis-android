package edu.uci.crayfis;

import android.content.SharedPreferences;

/**
 * Global configuration class.
 */
public final class CFConfig implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final CFConfig INSTANCE = new CFConfig();

    private static final String KEY_L1_THRESHOLD = "L1_thresh";
    private static final String KEY_L2_THRESHOLD = "L2_thresh";
    private static final String KEY_TARGET_EPM = "target_events_per_minute";
    private static final String KEY_CALIBRATION = "calibration_sample_frames";
    private static final String KEY_XB_PERIOD = "xb_period";

    private static final int DEFAULT_L1_THRESHOLD = 0;
    private static final int DEFAULT_L2_THRESHOLD = 5;
    private static final int DEFAULT_CALIBRATION_FRAMES = 1000;
    private static final int DEFAULT_STABILIZATION_FRAMES = 45;
    private static final int DEFAULT_TARGET_EPM = 60;
    private static final int DEFAULT_XB_PERIOD = 120;

    private int mL1Threshold;
    private int mL2Threshold;
    private int mCalibrationSampleFrames;
    private int mTargetEventsPerMinute;
    private int mStabilizationSampleFrames;
    private int mExposureBlockPeriod;

    private CFConfig() {
        mL1Threshold = DEFAULT_L1_THRESHOLD;
        mL2Threshold = DEFAULT_L2_THRESHOLD;
        mCalibrationSampleFrames = DEFAULT_CALIBRATION_FRAMES;
        mStabilizationSampleFrames = DEFAULT_STABILIZATION_FRAMES;
        mTargetEventsPerMinute = DEFAULT_TARGET_EPM;
        mExposureBlockPeriod = DEFAULT_XB_PERIOD;
    }

    /**
     * Get the threshold for camera frame capturing.
     *
     * @return int
     */
    public int getL1Threshold() {
        return mL1Threshold;
    }

    /**
     * Set the threshold for camera frame capturing.
     *
     * @param l1Threshold The new threshold.
     */
    public void setL1Threshold(int l1Threshold) {
        mL1Threshold = l1Threshold;
    }

    /**
     * Get the threshold for camera frame processing.
     *
     * @return int
     */
    public int getL2Threshold() {
        return mL2Threshold;
    }

    /**
     * Set the threshold for camera frame processing.
     *
     * @param l2Threshold The new threshold.
     */
    public void setL2Threshold(int l2Threshold) {
        mL2Threshold = l2Threshold;
    }

    /**
     * How many frames to sample during calibration.  More frames is longer but gives better
     * statistics.
     *
     * @return int
     */
    public int getCalibrationSampleFrames() {
        return mCalibrationSampleFrames;
    }

    /**
     * Number of frames to pass during stabilization periods.
     *
     * @return int
     */
    public int getStabilizationSampleFrames() {
        return mStabilizationSampleFrames;
    }

    /**
     * Targeted max. number of events per minute to allow.  Lower rate => higher threshold.
     *
     * @return int
     */
    public int getTargetEventsPerMinute() {
        return mTargetEventsPerMinute;
    }

    /**
     * The nominal period for an exposure block (in seconds)
     *
     * @return int
     */
    public int getExposureBlockPeriod() {
        return mExposureBlockPeriod;
    }

    /**
     * Get the instance of the configuration.
     *
     * @return {@link edu.uci.crayfis.CFConfig}
     */
    public static CFConfig getInstance() {
        return INSTANCE;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL1Threshold = sharedPreferences.getInt(KEY_L1_THRESHOLD, DEFAULT_L1_THRESHOLD);
        mL2Threshold = sharedPreferences.getInt(KEY_L2_THRESHOLD, DEFAULT_L2_THRESHOLD);
        mCalibrationSampleFrames = sharedPreferences.getInt(KEY_CALIBRATION, DEFAULT_CALIBRATION_FRAMES);
        mTargetEventsPerMinute = sharedPreferences.getInt(KEY_TARGET_EPM, DEFAULT_TARGET_EPM);
        mExposureBlockPeriod = sharedPreferences.getInt(KEY_XB_PERIOD, DEFAULT_XB_PERIOD);
    }
}
