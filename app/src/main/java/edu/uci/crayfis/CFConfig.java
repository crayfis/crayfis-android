package edu.uci.crayfis;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import edu.uci.crayfis.server.ServerCommand;

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
    private static final String KEY_QUAL_BG_AVG = "qual_bg_avg";
    private static final String KEY_QUAL_BG_VAR = "qual_bg_var";
    private static final String KEY_MAX_UPLOAD_INTERVAL = "max_upload_interval";
    private static final String KEY_MAX_CHUNK_SIZE = "max_chunk_size";
    private static final String KEY_CACHE_UPLOAD_INTERVAL = "min_cache_upload_interval";
    private static final String KEY_CURRENT_EXPERIMENT = "current_experiment";
    private static final String KEY_DEVICE_NICKNAME = "device_nickname";


    private static final int DEFAULT_L1_THRESHOLD = 0;
    private static final int DEFAULT_L2_THRESHOLD = 5;
    private static final int DEFAULT_CALIBRATION_FRAMES = 1000;
    private static final int DEFAULT_STABILIZATION_FRAMES = 45;
    private static final float DEFAULT_TARGET_EPM = 60;
    private static final int DEFAULT_XB_PERIOD = 120;
    private static final float DEFAULT_BG_AVG_CUT = 30;
    private static final float DEFAULT_BG_VAR_CUT = 5;
    private static final int DEFAULT_MAX_UPLOAD_INTERVAL = 180;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 250000;
    private static final int DEFAULT_CACHE_UPLOAD_INTERVAL = 30;
    private static final String DEFAULT_CURRENT_EXPERIMENT = null;
    private static final String DEFAULT_DEVICE_NICKNAME = null;

    private int mL1Threshold;
    private int mL2Threshold;
    private int mCalibrationSampleFrames;
    private float mTargetEventsPerMinute;
    private int mStabilizationSampleFrames;
    private int mExposureBlockPeriod;
    private float mQualityBgAverage;
    private float mQualityBgVariance;
    private int mMaxUploadInterval;
    private int mMaxChunkSize;
    private int mCacheUploadInterval;
    private String mCurrentExperiment;
    private String mDeviceNickname;

    private CFConfig() {
        mL1Threshold = DEFAULT_L1_THRESHOLD;
        mL2Threshold = DEFAULT_L2_THRESHOLD;
        mCalibrationSampleFrames = DEFAULT_CALIBRATION_FRAMES;
        mStabilizationSampleFrames = DEFAULT_STABILIZATION_FRAMES;
        mTargetEventsPerMinute = DEFAULT_TARGET_EPM;
        mExposureBlockPeriod = DEFAULT_XB_PERIOD;
        mQualityBgAverage = DEFAULT_BG_AVG_CUT;
        mQualityBgVariance = DEFAULT_BG_VAR_CUT;
        mMaxUploadInterval = DEFAULT_MAX_UPLOAD_INTERVAL;
        mMaxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        mCacheUploadInterval = DEFAULT_CACHE_UPLOAD_INTERVAL;
        mCurrentExperiment = DEFAULT_CURRENT_EXPERIMENT;
        mDeviceNickname = DEFAULT_DEVICE_NICKNAME;
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
     * @return float
     */
    public float getTargetEventsPerMinute() {
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
     * FIXME I'm not 100% sure what this is.
     *
     * @return int
     */
    public float getQualityBgAverage() {
        return mQualityBgAverage;
    }

    /**
     * FIXME I'm not 100% sure what this is.
     *
     * @return int
     */
    public float getQualityBgVariance() {
        return mQualityBgVariance;
    }

    /**
     * Get the instance of the configuration.
     *
     * @return {@link edu.uci.crayfis.CFConfig}
     */
    public static CFConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Get the maximum upload interval.
     *
     * @return int
     */
    public int getMaxUploadInterval() {
        return mMaxUploadInterval;
    }

    /**
     * Get the maximum chunk size.
     *
     * @return int
     */
    public int getMaxChunkSize() {
        return mMaxChunkSize;
    }

    /**
     * Get the cache upload interval.
     *
     * @return int
     */
    public int getCacheUploadInterval() {
        return mCacheUploadInterval;
    }

    /**
     * Get the current experiment.
     *
     * @return String or {@code null} if not set.
     */
    @Nullable
    public String getCurrentExperiment() {
        return mCurrentExperiment;
    }

    /**
     * Get the device nickname.
     *
     * @return String or {@code null} if not set.
     */
    @Nullable
    public String getDeviceNickname() {
        return mDeviceNickname;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL1Threshold = sharedPreferences.getInt(KEY_L1_THRESHOLD, DEFAULT_L1_THRESHOLD);
        mL2Threshold = sharedPreferences.getInt(KEY_L2_THRESHOLD, DEFAULT_L2_THRESHOLD);
        mCalibrationSampleFrames = sharedPreferences.getInt(KEY_CALIBRATION, DEFAULT_CALIBRATION_FRAMES);
        mTargetEventsPerMinute = sharedPreferences.getFloat(KEY_TARGET_EPM, DEFAULT_TARGET_EPM);
        mExposureBlockPeriod = sharedPreferences.getInt(KEY_XB_PERIOD, DEFAULT_XB_PERIOD);
        mQualityBgAverage = sharedPreferences.getFloat(KEY_QUAL_BG_AVG, DEFAULT_BG_AVG_CUT);
        mQualityBgVariance = sharedPreferences.getFloat(KEY_QUAL_BG_VAR, DEFAULT_BG_VAR_CUT);
        mMaxUploadInterval = sharedPreferences.getInt(KEY_MAX_UPLOAD_INTERVAL, DEFAULT_MAX_UPLOAD_INTERVAL);
        mMaxChunkSize = sharedPreferences.getInt(KEY_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE);
        mCacheUploadInterval = sharedPreferences.getInt(KEY_CACHE_UPLOAD_INTERVAL, DEFAULT_CACHE_UPLOAD_INTERVAL);
        mCurrentExperiment = sharedPreferences.getString(KEY_CURRENT_EXPERIMENT, DEFAULT_CURRENT_EXPERIMENT);
        mDeviceNickname = sharedPreferences.getString(KEY_DEVICE_NICKNAME, DEFAULT_DEVICE_NICKNAME);
    }

    /**
     * Update configuration based on commands from the server.
     *
     * FIXME This is not saving to the shared preferences yet.
     *
     * @param serverCommand {@link edu.uci.crayfis.server.ServerCommand}
     */
    public void updateFromServer(@NonNull final ServerCommand serverCommand) {
        if (serverCommand.getL1Threshold() != null) {
            mL1Threshold = serverCommand.getL1Threshold();
        }
        if (serverCommand.getL2Threshold() != null) {
            mL2Threshold = serverCommand.getL2Threshold();
        }
        if (serverCommand.getEventsPerMinute() != null) {
            mTargetEventsPerMinute = serverCommand.getEventsPerMinute();
        }
        if (serverCommand.getCalibrationSampleFrames() != null) {
            mCalibrationSampleFrames = serverCommand.getCalibrationSampleFrames();
        }
        if (serverCommand.getTargetExposureBlockPeriod() != null) {
            mExposureBlockPeriod = serverCommand.getTargetExposureBlockPeriod();
        }
        if (serverCommand.getQualityBgAverage() != null) {
            mQualityBgAverage = serverCommand.getQualityBgAverage();
        }
        if (serverCommand.getQualityBgVariance() != null) {
            mQualityBgVariance = serverCommand.getQualityBgVariance();
        }
        if (serverCommand.getCurrentExperiment() != null) {
            mCurrentExperiment = serverCommand.getCurrentExperiment();
        }
        if (serverCommand.getDeviceNickname() != null) {
            mDeviceNickname = serverCommand.getDeviceNickname();
        }
    }
}
