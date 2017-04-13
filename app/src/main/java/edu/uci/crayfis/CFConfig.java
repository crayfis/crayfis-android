package edu.uci.crayfis;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import edu.uci.crayfis.camera.ResolutionSpec;
import edu.uci.crayfis.server.ServerCommand;
import edu.uci.crayfis.util.CFLog;

/**
 * Global configuration class.
 */
public final class CFConfig implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final CFConfig INSTANCE = new CFConfig();

    private static final String KEY_L1_TRIGGER = "L1_trigger";
    private static final String KEY_L2_TRIGGER = "L2_trigger";
    private static final String KEY_L1_THRESHOLD = "L1_thresh";
    private static final String KEY_L2_THRESHOLD = "L2_thresh";
    private static final String KEY_TARGET_EPM = "target_events_per_minute";
    private static final String KEY_CALIBRATION = "calibration_sample_frames";
    private static final String KEY_XB_PERIOD = "xb_period";
    private static final String KEY_QUAL_BG_AVG = "qual_bg_avg";
    private static final String KEY_QUAL_BG_VAR = "qual_bg_var";
    private static final String KEY_QUAL_ORIENT = "qual_orient";
    private static final String KEY_QUAL_PIX_FRAC = "qual_pix_frac";
    private static final String KEY_MAX_UPLOAD_INTERVAL = "max_upload_interval";
    private static final String KEY_MAX_CHUNK_SIZE = "max_chunk_size";
    private static final String KEY_CACHE_UPLOAD_INTERVAL = "min_cache_upload_interval";
    private static final String KEY_CURRENT_EXPERIMENT = "current_experiment";
    private static final String KEY_DEVICE_NICKNAME = "device_nickname";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_SCORE = "account_score";
    private static final String KEY_UPDATE_URL = "update_url";
    private static final String KEY_TRIGGER_LOCK = "prefTriggerLock";
    private static final String KEY_TARGET_RESOLUTION_STR = "prefResolution";
    private static final String KEY_CAMERA_SELECT_MODE = "prefCameraSelectMode";


    // FIXME: not sure if it makes sense to store the L1/L2 thresholds; they are always
    // either determined via calibration, or are set by the server (until the next calibration).
    private static final String DEFAULT_L1_TRIGGER = "default";
    private static final String DEFAULT_L2_TRIGGER = "default";
    private static final int DEFAULT_L1_THRESHOLD = 0;
    private static final int DEFAULT_L2_THRESHOLD = 5;
    private static final int DEFAULT_CALIBRATION_FRAMES = 1000;
    private static final int DEFAULT_STABILIZATION_FRAMES = 45;
    private static final float DEFAULT_TARGET_EPM = 60;
    private static final int DEFAULT_XB_PERIOD = 120;
    private static final float DEFAULT_BG_AVG_CUT = 5f;
    private static final float DEFAULT_BG_VAR_CUT = 5;
    private static final float DEFAULT_ORIENT_CUT = 3f;
    private static final float DEFAULT_PIX_FRAC_CUT = 0.10f;
    private static final int DEFAULT_MAX_UPLOAD_INTERVAL = 180;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 250000;
    private static final int DEFAULT_CACHE_UPLOAD_INTERVAL = 30;
    private static final String DEFAULT_CURRENT_EXPERIMENT = null;
    private static final String DEFAULT_DEVICE_NICKNAME = null;
    private static final String DEFAULT_ACCOUNT_NAME = null;
    private static final float DEFAULT_ACCOUNT_SCORE = (float)0.;
    private static final String DEFAULT_UPDATE_URL = "";
    private static final boolean DEFAULT_TRIGGER_LOCK = false;
    private static final String DEFAULT_TARGET_RESOLUTION_STR = "1080p";
    private static final String DEFAULT_CAMERA_SELECT_MODE
            = Resources.getSystem().getString(R.string.camera_select_face_down);

    private String mL1Trigger;
    private String mL2Trigger;
    private int mL1Threshold;
    private int mL2Threshold;
    private int mCalibrationSampleFrames;
    private float mTargetEventsPerMinute;
    private int mStabilizationSampleFrames;
    private int mExposureBlockPeriod;
    private float mQualityBgAverage;
    private float mQualityBgVariance;
    private float mQualityOrient;
    private float mQualityPixFraction;
    private int mMaxUploadInterval;
    private int mMaxChunkSize;
    private int mCacheUploadInterval;
    private String mCurrentExperiment;
    private String mDeviceNickname;
    private String mAccountName;
    private float mAccountScore;
    private String mUpdateURL;
    private boolean mTriggerLock;
    private String mTargetResolutionStr;
    private int mCameraSelectMode;

    private CFConfig() {
        // FIXME: shouldn't we initialize based on the persistent config values?
        mL1Trigger = DEFAULT_L1_TRIGGER;
        mL2Trigger = DEFAULT_L2_TRIGGER;
        mL1Threshold = DEFAULT_L1_THRESHOLD;
        mL2Threshold = DEFAULT_L2_THRESHOLD;
        mCalibrationSampleFrames = DEFAULT_CALIBRATION_FRAMES;
        mStabilizationSampleFrames = DEFAULT_STABILIZATION_FRAMES;
        mTargetEventsPerMinute = DEFAULT_TARGET_EPM;
        mExposureBlockPeriod = DEFAULT_XB_PERIOD;
        mQualityBgAverage = DEFAULT_BG_AVG_CUT;
        mQualityBgVariance = DEFAULT_BG_VAR_CUT;
        mQualityOrient = DEFAULT_ORIENT_CUT;
        mQualityPixFraction = DEFAULT_PIX_FRAC_CUT;
        mMaxUploadInterval = DEFAULT_MAX_UPLOAD_INTERVAL;
        mMaxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        mCacheUploadInterval = DEFAULT_CACHE_UPLOAD_INTERVAL;
        mCurrentExperiment = DEFAULT_CURRENT_EXPERIMENT;
        mDeviceNickname = DEFAULT_DEVICE_NICKNAME;
        mAccountName = DEFAULT_ACCOUNT_NAME;
        mAccountScore = DEFAULT_ACCOUNT_SCORE;
        mUpdateURL = DEFAULT_UPDATE_URL;
        mTriggerLock = DEFAULT_TRIGGER_LOCK;
        mTargetResolutionStr = DEFAULT_TARGET_RESOLUTION_STR;
        mCameraSelectMode = CameraSelector.getCameraSelectMode(DEFAULT_CAMERA_SELECT_MODE);
    }

    public String getL1Trigger() {
        return mL1Trigger;
    }

    public String getL2Trigger() {
        return mL2Trigger;
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
     * Get the maximum fraction of sensor pixels which can be accepted as L2
     * before the event is flagged as "bad".
     *
     * @return float
     */
    public float getQualityPixFraction() { return mQualityPixFraction; }

    /**
     * Get the maximum average pixel value (before any cuts) allowed before the
     * event is flagged as "bad".
     *
     * @return int
     */
    public float getQualityBgAverage() {
        return mQualityBgAverage;
    }

    /**
     * The the maximum variance in pixel values (before any cuts) allowed before the
     * event is flagged as "bad".
     *
     * @return int
     */
    public float getQualityBgVariance() {
        return mQualityBgVariance;
    }

    /**
     * In face-down mode, rotations along the x or y axis (in degrees) greater
     * than this angle are flagged as "bad".
     *
     * @return float
     */
    public float getQualityOrientation() { return mQualityOrient; }

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


    public void setAccountName(@Nullable final String accountName) {
        mAccountName = accountName;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public float getAccountScore() { return mAccountScore; }
    public String getUpdateURL() { return mUpdateURL; }

    /***
     * Ask whether the trigger lock is engaged.
     * @return True if the trigger is locked, else false.
     */
    public boolean getTriggerLock() { return mTriggerLock; }

    @Nullable
    public ResolutionSpec getTargetResolution() { return ResolutionSpec.fromString(mTargetResolutionStr); }

    /**
     * Get the camera selection mode, as defined in the CameraSelector class
     *
     * @return int
     */
    public int getCameraSelectMode() { return mCameraSelectMode; }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL1Trigger = sharedPreferences.getString(KEY_L1_TRIGGER, DEFAULT_L1_TRIGGER);
        mL2Trigger = sharedPreferences.getString(KEY_L2_TRIGGER, DEFAULT_L2_TRIGGER);
        mL1Threshold = sharedPreferences.getInt(KEY_L1_THRESHOLD, DEFAULT_L1_THRESHOLD);
        mL2Threshold = sharedPreferences.getInt(KEY_L2_THRESHOLD, DEFAULT_L2_THRESHOLD);
        mCalibrationSampleFrames = sharedPreferences.getInt(KEY_CALIBRATION, DEFAULT_CALIBRATION_FRAMES);
        mTargetEventsPerMinute = sharedPreferences.getFloat(KEY_TARGET_EPM, DEFAULT_TARGET_EPM);
        mExposureBlockPeriod = sharedPreferences.getInt(KEY_XB_PERIOD, DEFAULT_XB_PERIOD);
        mQualityBgAverage = sharedPreferences.getFloat(KEY_QUAL_BG_AVG, DEFAULT_BG_AVG_CUT);
        mQualityBgVariance = sharedPreferences.getFloat(KEY_QUAL_BG_VAR, DEFAULT_BG_VAR_CUT);
        mQualityBgAverage = sharedPreferences.getFloat(KEY_QUAL_ORIENT, DEFAULT_ORIENT_CUT);
        mQualityPixFraction = sharedPreferences.getFloat(KEY_QUAL_PIX_FRAC, DEFAULT_PIX_FRAC_CUT);
        mMaxUploadInterval = sharedPreferences.getInt(KEY_MAX_UPLOAD_INTERVAL, DEFAULT_MAX_UPLOAD_INTERVAL);
        mMaxChunkSize = sharedPreferences.getInt(KEY_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE);
        mCacheUploadInterval = sharedPreferences.getInt(KEY_CACHE_UPLOAD_INTERVAL, DEFAULT_CACHE_UPLOAD_INTERVAL);
        mCurrentExperiment = sharedPreferences.getString(KEY_CURRENT_EXPERIMENT, DEFAULT_CURRENT_EXPERIMENT);
        mDeviceNickname = sharedPreferences.getString(KEY_DEVICE_NICKNAME, DEFAULT_DEVICE_NICKNAME);
        mAccountName = sharedPreferences.getString(KEY_ACCOUNT_NAME, DEFAULT_ACCOUNT_NAME);
        mAccountScore = sharedPreferences.getFloat(KEY_ACCOUNT_SCORE, DEFAULT_ACCOUNT_SCORE);
        mUpdateURL = sharedPreferences.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL);
        mTriggerLock = sharedPreferences.getBoolean(KEY_TRIGGER_LOCK, DEFAULT_TRIGGER_LOCK);
        mTargetResolutionStr = sharedPreferences.getString(KEY_TARGET_RESOLUTION_STR, DEFAULT_TARGET_RESOLUTION_STR);
        String cameraSelectStr = sharedPreferences.getString(KEY_CAMERA_SELECT_MODE, DEFAULT_CAMERA_SELECT_MODE);
        mCameraSelectMode = CameraSelector.getCameraSelectMode(cameraSelectStr);

    }

    /**
     * Update configuration based on commands from the server.
     *
     * @param serverCommand {@link edu.uci.crayfis.server.ServerCommand}
     */
    public void updateFromServer(@NonNull final ServerCommand serverCommand) {
        if (serverCommand == null) return;
        CFLog.i("GOT command from server!");
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
        if (serverCommand.getQualityOrientation() != null) {
            mQualityOrient = serverCommand.getQualityOrientation();
        }
        if (serverCommand.getQualityPixFrac() != null) {
            mQualityPixFraction = serverCommand.getQualityPixFrac();
        }
        if (serverCommand.getCurrentExperiment() != null) {
            mCurrentExperiment = serverCommand.getCurrentExperiment();
        }
        if (serverCommand.getDeviceNickname() != null) {
            mDeviceNickname = serverCommand.getDeviceNickname();
        }
        if (serverCommand.getMaxUploadInterval() != null) {
            mMaxUploadInterval = serverCommand.getMaxUploadInterval();
        }
        if (serverCommand.getMaxChunkSize() != null) {
            mMaxChunkSize = serverCommand.getMaxChunkSize();
        }
        if (serverCommand.getAccountName() != null) {
            mAccountName = serverCommand.getAccountName();

        }
        if (serverCommand.getAccountScore() != null) {
            mAccountScore = serverCommand.getAccountScore();
        }
        if (serverCommand.getUpdateURL() != null) {
            mUpdateURL = serverCommand.getUpdateURL();
        }
        if (serverCommand.getL1Trigger() != null) {
            mL1Trigger = serverCommand.getL1Trigger();
        }
        if (serverCommand.getL2Trigger() != null) {
            mL2Trigger = serverCommand.getL2Trigger();
        }
        if (serverCommand.getTriggerLock() != null) {
            mTriggerLock = serverCommand.getTriggerLock();
        }
        if (serverCommand.getResolution() != null) {
            mTargetResolutionStr = serverCommand.getResolution();
        }
        if (serverCommand.getCameraSelectMode() != null) {
            mCameraSelectMode = serverCommand.getCameraSelectMode();
        }
    }

    public void save(@NonNull final SharedPreferences sharedPreferences) {
        sharedPreferences.edit()
                .putString(KEY_L1_TRIGGER, mL1Trigger)
                .putString(KEY_L2_TRIGGER, mL2Trigger)
                .putInt(KEY_L1_THRESHOLD, mL1Threshold)
                .putInt(KEY_L2_THRESHOLD, mL2Threshold)
                .putInt(KEY_CALIBRATION, mCalibrationSampleFrames)
                .putFloat(KEY_TARGET_EPM, mTargetEventsPerMinute)
                .putInt(KEY_XB_PERIOD, mExposureBlockPeriod)
                .putFloat(KEY_QUAL_BG_AVG, mQualityBgAverage)
                .putFloat(KEY_QUAL_BG_VAR, mQualityBgVariance)
                .putFloat(KEY_QUAL_ORIENT, mQualityOrient)
                .putFloat(KEY_QUAL_PIX_FRAC, mQualityPixFraction)
                .putInt(KEY_MAX_UPLOAD_INTERVAL, mMaxUploadInterval)
                .putInt(KEY_MAX_CHUNK_SIZE, mMaxChunkSize)
                .putString(KEY_CURRENT_EXPERIMENT, mCurrentExperiment)
                .putString(KEY_DEVICE_NICKNAME, mDeviceNickname)
                .putString(KEY_ACCOUNT_NAME,mAccountName)
                .putFloat(KEY_ACCOUNT_SCORE,mAccountScore)
                .putString(KEY_UPDATE_URL,mUpdateURL)
                .putBoolean(KEY_TRIGGER_LOCK,mTriggerLock)
                .putString(KEY_TARGET_RESOLUTION_STR,mTargetResolutionStr)
                .putInt(KEY_CAMERA_SELECT_MODE,mCameraSelectMode)
                .apply();
    }
}
