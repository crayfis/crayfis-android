package io.crayfis.android.server;

import android.content.SharedPreferences;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.camera.ResolutionSpec;
import io.crayfis.android.util.CFLog;

/**
 * Global configuration class.
 */
public final class CFConfig implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final CFConfig INSTANCE = new CFConfig();

    private static final String KEY_L1_TRIGGER = "L1_trigger";
    private static final String KEY_L2_TRIGGER = "L2_trigger";
    private static final String KEY_WEIGHTS = "precal_weights_";
    private static final String KEY_HOTCELLS = "hotcells_";
    private static final String KEY_PRECAL_MOST = "precal_uuid_most_";
    private static final String KEY_PRECAL_LEAST = "precal_uuid_least_";
    private static final String KEY_LAST_PRECAL_TIME = "last_precal_time_";
    private static final String KEY_LAST_PRECAL_RES_X = "last_precal_res_x_";
    private static final String KEY_L1_THRESHOLD = "L1_thresh";
    private static final String KEY_L2_THRESHOLD = "L2_thresh";
    private static final String KEY_TARGET_EPM = "target_events_per_minute";
    private static final String KEY_WEIGHTING_FRAMES = "weighting_sample_frames";
    private static final String KEY_HOTCELL_FRAMES = "hotcell_sample_frames";
    private static final String KEY_HOTCELL_THRESH = "hotcell_thresh";
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
    private static final String KEY_TRIGGER_LOCK = "prefTriggerLock";
    private static final String KEY_TARGET_RESOLUTION_STR = "prefResolution";
    private static final String KEY_TARGET_FPS = "prefFPS";
    private static final String KEY_CAMERA_SELECT_MODE = "prefCameraSelectMode";
    private static final String KEY_BATTERY_OVERHEAT_TEMP = "battery_overheat_temp";
    private static final String KEY_PRECAL_RESET_TIME = "precal_reset_time";




    // FIXME: not sure if it makes sense to store the L1/L2 thresholds; they are always
    // either determined via calibration, or are set by the server (until the next calibration).
    private final int N_CAMERAS;
    private static final String DEFAULT_L1_TRIGGER = "default";
    private static final String DEFAULT_L2_TRIGGER = "default";
    private static final int DEFAULT_L1_THRESHOLD = 0;
    private static final int DEFAULT_L2_THRESHOLD = 5;
    private static final Set<String> DEFAULT_HOTCELLS = new HashSet<>();
    private static final int DEFAULT_WEIGHTING_FRAMES = 1000;
    private static final int DEFAULT_HOTCELL_FRAMES = 10000;
    private static final float DEFAULT_HOTCELL_THRESH = .002f;
    private static final int DEFAULT_CALIBRATION_FRAMES = 1000;
    private static final int DEFAULT_STABILIZATION_FRAMES = 45;
    private static final float DEFAULT_TARGET_EPM = 30;
    private static final int DEFAULT_XB_PERIOD = 120;
    private static final float DEFAULT_BG_AVG_CUT = 10f;
    private static final float DEFAULT_BG_VAR_CUT = 30f;
    private static final double DEFAULT_ORIENT_CUT = (10 * Math.PI/180);
    private static final float DEFAULT_PIX_FRAC_CUT = 0.10f;
    private static final int DEFAULT_MAX_UPLOAD_INTERVAL = 180;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 250000;
    private static final int DEFAULT_CACHE_UPLOAD_INTERVAL = 30;
    private static final String DEFAULT_CURRENT_EXPERIMENT = null;
    private static final String DEFAULT_DEVICE_NICKNAME = null;
    private static final String DEFAULT_ACCOUNT_NAME = null;
    private static final float DEFAULT_ACCOUNT_SCORE = (float)0.;
    private static final boolean DEFAULT_TRIGGER_LOCK = false;
    private static final String DEFAULT_TARGET_RESOLUTION_STR = "1080p";
    private static final String DEFAULT_TARGET_FPS = "15";
    private static final int DEFAULT_CAMERA_SELECT_MODE = CFApplication.MODE_FACE_DOWN;
    private static final int DEFAULT_BATTERY_OVERHEAT_TEMP = 410;
    private static final Long DEFAULT_PRECAL_RESET_TIME = 7*24*3600 * 1000L;

    private String mL1Trigger;
    private String mL2Trigger;
    private List<Set<String>> mHotcells;
    private String[] mPrecalWeights;
    private UUID[] mPrecalUUID;
    private long[] mLastPrecalTime;
    private int[] mLastPrecalResX;
    private int mL1Threshold;
    private int mL2Threshold;
    private int mWeightingSampleFrames;
    private int mHotcellSampleFrames;
    private float mHotcellThresh;
    private int mCalibrationSampleFrames;
    private float mTargetEventsPerMinute;
    private int mStabilizationSampleFrames;
    private int mExposureBlockPeriod;
    private float mQualityBgAverage;
    private float mQualityBgVariance;
    private double mQualityOrientCosine;
    private float mQualityPixFraction;
    private int mMaxUploadInterval;
    private int mMaxChunkSize;
    private int mCacheUploadInterval;
    private String mCurrentExperiment;
    private String mDeviceNickname;
    private String mAccountName;
    private float mAccountScore;
    private boolean mTriggerLock;
    private String mTargetResolutionStr;
    private String mTargetFPS;
    private int mCameraSelectMode;
    private int mBatteryOverheatTemp;
    private Long mPrecalResetTime; // ms

    private CFConfig() {
        // FIXME: shouldn't we initialize based on the persistent config values?
        N_CAMERAS = Camera.getNumberOfCameras();

        mL1Trigger = DEFAULT_L1_TRIGGER;
        mL2Trigger = DEFAULT_L2_TRIGGER;
        mL1Threshold = DEFAULT_L1_THRESHOLD;
        mL2Threshold = DEFAULT_L2_THRESHOLD;
        mWeightingSampleFrames = DEFAULT_WEIGHTING_FRAMES;
        mHotcellSampleFrames = DEFAULT_HOTCELL_FRAMES;
        mHotcellThresh = DEFAULT_HOTCELL_THRESH;
        mCalibrationSampleFrames = DEFAULT_CALIBRATION_FRAMES;
        mStabilizationSampleFrames = DEFAULT_STABILIZATION_FRAMES;
        mTargetEventsPerMinute = DEFAULT_TARGET_EPM;
        mExposureBlockPeriod = DEFAULT_XB_PERIOD;
        mQualityBgAverage = DEFAULT_BG_AVG_CUT;
        mQualityBgVariance = DEFAULT_BG_VAR_CUT;
        mQualityOrientCosine = Math.cos(DEFAULT_ORIENT_CUT);
        mQualityPixFraction = DEFAULT_PIX_FRAC_CUT;
        mMaxUploadInterval = DEFAULT_MAX_UPLOAD_INTERVAL;
        mMaxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        mCacheUploadInterval = DEFAULT_CACHE_UPLOAD_INTERVAL;
        mCurrentExperiment = DEFAULT_CURRENT_EXPERIMENT;
        mDeviceNickname = DEFAULT_DEVICE_NICKNAME;
        mAccountName = DEFAULT_ACCOUNT_NAME;
        mAccountScore = DEFAULT_ACCOUNT_SCORE;
        mTriggerLock = DEFAULT_TRIGGER_LOCK;
        mTargetResolutionStr = DEFAULT_TARGET_RESOLUTION_STR;
        mCameraSelectMode = DEFAULT_CAMERA_SELECT_MODE;
        mTargetFPS = DEFAULT_TARGET_FPS;
        mBatteryOverheatTemp = DEFAULT_BATTERY_OVERHEAT_TEMP;
        mPrecalResetTime = DEFAULT_PRECAL_RESET_TIME;

        mHotcells = new ArrayList<>(N_CAMERAS);
        for(int i=0; i<N_CAMERAS; i++) {
            mHotcells.add(new HashSet<String>());
        }
    }

    public String getL1Trigger() {
        return mL1Trigger;
    }

    public String getL2Trigger() {
        return mL2Trigger;
    }

    public String getPrecalWeights(int cameraId) {
        return mPrecalWeights[cameraId];
    }

    public void setPrecalWeights(int cameraId, String s) {
        mPrecalWeights[cameraId] = s;
    }

    public Set<Integer> getHotcells(int cameraId) {
        Set<Integer> intSet = new HashSet<>(mHotcells.get(cameraId).size());
        for(String pos: mHotcells.get(cameraId)) {
            intSet.add(Integer.parseInt(pos,16));
        }
        return intSet;
    }

    public void setHotcells(int cameraId, Set<Integer> hotcells) {
        mHotcells.get(cameraId).clear();
        for(Integer pos: hotcells) {
            mHotcells.get(cameraId).add(Integer.toHexString(pos));
        }
    }

    public UUID getPrecalId(int cameraId) {
        return mPrecalUUID[cameraId];
    }

    public void setPrecalId(int cameraId, UUID precalId) {
        mPrecalUUID[cameraId] = precalId;
    }

    public long getLastPrecalTime(int cameraId) {
        return mLastPrecalTime[cameraId];
    }

    public void setLastPrecalTime(int cameraId, long t) {
        mLastPrecalTime[cameraId] = t;
    }

    public int getLastPrecalResX(int cameraId) {
        return mLastPrecalResX[cameraId];
    }

    public void setLastPrecalResX(int cameraId, int resX) {
        mLastPrecalResX[cameraId] = resX;
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

    public int getWeightingSampleFrames() {
        return mWeightingSampleFrames;
    }

    public int getHotcellSampleFrames() { return mHotcellSampleFrames; }

    public float getHotcellThresh() { return mHotcellThresh; }

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
     * In face-down mode, rotations along the x or y axis (in radians) greater
     * than this angle are flagged as "bad".
     *
     * @return float
     */
    public double getQualityOrientationCosine() { return mQualityOrientCosine; }

    /**
     * Get the instance of the configuration.
     *
     * @return {@link CFConfig}
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

    /***
     * Ask whether the trigger lock is engaged.
     * @return True if the trigger is locked, else false.
     */
    public boolean getTriggerLock() { return mTriggerLock; }

    @Nullable
    public ResolutionSpec getTargetResolution() { return ResolutionSpec.fromString(mTargetResolutionStr); }

    public int getTargetFPS() {
        try {
            return Integer.parseInt(mTargetFPS);
        } catch(NumberFormatException e) {
            // FIXME: should be a better way to check for long exposure
            return 0;
        }
    }

    /**
     * Get the camera selection mode
     *
     * @return int
     */
    public int getCameraSelectMode() {
        return mCameraSelectMode;
    }

    public int getBatteryOverheatTemp() {
        return mBatteryOverheatTemp;
    }

    public Long getPrecalResetTime() {
        return mPrecalResetTime;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL1Trigger = sharedPreferences.getString(KEY_L1_TRIGGER, DEFAULT_L1_TRIGGER);
        mL2Trigger = sharedPreferences.getString(KEY_L2_TRIGGER, DEFAULT_L2_TRIGGER);
        mL1Threshold = sharedPreferences.getInt(KEY_L1_THRESHOLD, DEFAULT_L1_THRESHOLD);
        mL2Threshold = sharedPreferences.getInt(KEY_L2_THRESHOLD, DEFAULT_L2_THRESHOLD);
        mWeightingSampleFrames = sharedPreferences.getInt(KEY_WEIGHTING_FRAMES, DEFAULT_WEIGHTING_FRAMES);
        mHotcellSampleFrames = sharedPreferences.getInt(KEY_HOTCELL_FRAMES, DEFAULT_HOTCELL_FRAMES);
        mHotcellThresh = sharedPreferences.getFloat(KEY_HOTCELL_THRESH, DEFAULT_HOTCELL_THRESH);
        mCalibrationSampleFrames = sharedPreferences.getInt(KEY_CALIBRATION, DEFAULT_CALIBRATION_FRAMES);
        mTargetEventsPerMinute = sharedPreferences.getFloat(KEY_TARGET_EPM, DEFAULT_TARGET_EPM);
        mExposureBlockPeriod = sharedPreferences.getInt(KEY_XB_PERIOD, DEFAULT_XB_PERIOD);
        mQualityBgAverage = sharedPreferences.getFloat(KEY_QUAL_BG_AVG, DEFAULT_BG_AVG_CUT);
        mQualityBgVariance = sharedPreferences.getFloat(KEY_QUAL_BG_VAR, DEFAULT_BG_VAR_CUT);
        mQualityOrientCosine = sharedPreferences.getFloat(KEY_QUAL_ORIENT, (float)Math.cos(DEFAULT_ORIENT_CUT));
        mQualityPixFraction = sharedPreferences.getFloat(KEY_QUAL_PIX_FRAC, DEFAULT_PIX_FRAC_CUT);
        mMaxUploadInterval = sharedPreferences.getInt(KEY_MAX_UPLOAD_INTERVAL, DEFAULT_MAX_UPLOAD_INTERVAL);
        mMaxChunkSize = sharedPreferences.getInt(KEY_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE);
        mCacheUploadInterval = sharedPreferences.getInt(KEY_CACHE_UPLOAD_INTERVAL, DEFAULT_CACHE_UPLOAD_INTERVAL);
        mCurrentExperiment = sharedPreferences.getString(KEY_CURRENT_EXPERIMENT, DEFAULT_CURRENT_EXPERIMENT);
        mDeviceNickname = sharedPreferences.getString(KEY_DEVICE_NICKNAME, DEFAULT_DEVICE_NICKNAME);
        mAccountName = sharedPreferences.getString(KEY_ACCOUNT_NAME, DEFAULT_ACCOUNT_NAME);
        mAccountScore = sharedPreferences.getFloat(KEY_ACCOUNT_SCORE, DEFAULT_ACCOUNT_SCORE);
        mTriggerLock = sharedPreferences.getBoolean(KEY_TRIGGER_LOCK, DEFAULT_TRIGGER_LOCK);
        mTargetResolutionStr = sharedPreferences.getString(KEY_TARGET_RESOLUTION_STR, DEFAULT_TARGET_RESOLUTION_STR);
        mTargetFPS = sharedPreferences.getString(KEY_TARGET_FPS, DEFAULT_TARGET_FPS);
        String cameraSelectStr = sharedPreferences.getString(KEY_CAMERA_SELECT_MODE,
                Integer.toString(DEFAULT_CAMERA_SELECT_MODE));
        mCameraSelectMode = Integer.parseInt(cameraSelectStr);
        mPrecalResetTime = sharedPreferences.getLong(KEY_PRECAL_RESET_TIME, DEFAULT_PRECAL_RESET_TIME);

        mPrecalWeights = new String[N_CAMERAS];
        mPrecalUUID = new UUID[N_CAMERAS];
        mLastPrecalTime = new long[N_CAMERAS];
        mLastPrecalResX = new int[N_CAMERAS];
        for(int i=0; i<N_CAMERAS; i++) {
            mPrecalWeights[i] = sharedPreferences.getString(KEY_WEIGHTS + i, null);
            mHotcells.add(i, sharedPreferences.getStringSet(KEY_HOTCELLS + i, DEFAULT_HOTCELLS));
            long mostSignificant = sharedPreferences.getLong(KEY_PRECAL_MOST + i, 0L);
            long leastSignificant = sharedPreferences.getLong(KEY_PRECAL_LEAST + i, 0L);
            mPrecalUUID[i] = new UUID(mostSignificant, leastSignificant);
            mLastPrecalTime[i] = sharedPreferences.getLong(KEY_LAST_PRECAL_TIME + i, 0);
            mLastPrecalResX[i] = sharedPreferences.getInt(KEY_LAST_PRECAL_RES_X + i, -1);
        }

    }

    /**
     * Update configuration based on commands from the server.
     *
     * @param serverCommand {@link io.crayfis.android.server.ServerCommand}
     */
    void updateFromServer(@NonNull final ServerCommand serverCommand) {

        CFLog.i("GOT command from server!");
        if (serverCommand.getPrecalWeights() != null) {
            mPrecalWeights = serverCommand.getPrecalWeights();
        }
        if (serverCommand.getHotcells() != null) {
            mHotcells = serverCommand.getHotcells();
        }
        if (serverCommand.getLastPrecalTime() != null) {
            mLastPrecalTime = serverCommand.getLastPrecalTime();
        }
        if (serverCommand.getLastPrecalResX() != null) {
            mLastPrecalResX = serverCommand.getLastPrecalResX();
        }
        if (serverCommand.getPrecalId() != null) {
            mPrecalUUID = serverCommand.getPrecalId();
        }
        if (serverCommand.getL1Threshold() != null) {
            mL1Threshold = serverCommand.getL1Threshold();
        }
        if (serverCommand.getL2Threshold() != null) {
            mL2Threshold = serverCommand.getL2Threshold();
        }
        if (serverCommand.getEventsPerMinute() != null) {
            mTargetEventsPerMinute = serverCommand.getEventsPerMinute();
        }
        if (serverCommand.getWeightingSampleFrames() != null) {
            mWeightingSampleFrames = serverCommand.getWeightingSampleFrames();
        }
        if (serverCommand.getHotcellSampleFrames() != null) {
            mHotcellSampleFrames = serverCommand.getHotcellSampleFrames();
        }
        if (serverCommand.getHotcellThresh() != null) {
            mHotcellThresh = serverCommand.getHotcellThresh();
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
            mQualityOrientCosine = Math.cos(serverCommand.getQualityOrientation());
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
        if (serverCommand.getL1Trigger() != null) {
            mL1Trigger = serverCommand.getL1Trigger();
        }
        if (serverCommand.getL2Trigger() != null) {
            mL2Trigger = serverCommand.getL2Trigger();
        }
        if (serverCommand.getTriggerLock() != null) {
            mTriggerLock = serverCommand.getTriggerLock();
        }

        // if we're changing the camera settings, reconfigure it
        if (serverCommand.getResolution() != null) {
            mTargetResolutionStr = serverCommand.getResolution();
            CFCamera.getInstance().changeCamera();
        }
        if(serverCommand.getTargetFPS() != null) {
            mTargetFPS = serverCommand.getTargetFPS();
            CFCamera.getInstance().changeCamera();
        }

        if (serverCommand.getCameraSelectMode() != null) {
            mCameraSelectMode = serverCommand.getCameraSelectMode();
        }
        if (serverCommand.getBatteryOverheatTemp() != null) {
            mBatteryOverheatTemp = serverCommand.getBatteryOverheatTemp();
        }
        if (serverCommand.getPrecalResetTime() != null) {
            // in case we choose to only update through the server
            if(serverCommand.getPrecalResetTime() > 0) {
                mPrecalResetTime = serverCommand.getPrecalResetTime();
            } else {
                mPrecalResetTime = null;
            }
        }
    }

    public void save(@NonNull final SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        for(int i=0; i<N_CAMERAS; i++) {
            editor.putString(KEY_WEIGHTS + i, mPrecalWeights[i])
                    .putStringSet(KEY_HOTCELLS + i, mHotcells.get(i))
                    .putLong(KEY_PRECAL_MOST + i, mPrecalUUID[i].getMostSignificantBits())
                    .putLong(KEY_PRECAL_LEAST + i, mPrecalUUID[i].getLeastSignificantBits())
                    .putLong(KEY_LAST_PRECAL_TIME + i, mLastPrecalTime[i])
                    .putInt(KEY_LAST_PRECAL_RES_X + i, mLastPrecalResX[i]);
        }

        editor.putString(KEY_L1_TRIGGER, mL1Trigger)
                .putString(KEY_L2_TRIGGER, mL2Trigger)
                .putInt(KEY_L1_THRESHOLD, mL1Threshold)
                .putInt(KEY_L2_THRESHOLD, mL2Threshold)
                .putInt(KEY_WEIGHTING_FRAMES, mWeightingSampleFrames)
                .putInt(KEY_HOTCELL_FRAMES, mHotcellSampleFrames)
                .putFloat(KEY_HOTCELL_THRESH, mHotcellThresh)
                .putInt(KEY_CALIBRATION, mCalibrationSampleFrames)
                .putFloat(KEY_TARGET_EPM, mTargetEventsPerMinute)
                .putInt(KEY_XB_PERIOD, mExposureBlockPeriod)
                .putFloat(KEY_QUAL_BG_AVG, mQualityBgAverage)
                .putFloat(KEY_QUAL_BG_VAR, mQualityBgVariance)
                .putFloat(KEY_QUAL_ORIENT, (float)mQualityOrientCosine)
                .putFloat(KEY_QUAL_PIX_FRAC, mQualityPixFraction)
                .putInt(KEY_MAX_UPLOAD_INTERVAL, mMaxUploadInterval)
                .putInt(KEY_MAX_CHUNK_SIZE, mMaxChunkSize)
                .putString(KEY_CURRENT_EXPERIMENT, mCurrentExperiment)
                .putString(KEY_DEVICE_NICKNAME, mDeviceNickname)
                .putString(KEY_ACCOUNT_NAME,mAccountName)
                .putFloat(KEY_ACCOUNT_SCORE,mAccountScore)
                .putBoolean(KEY_TRIGGER_LOCK,mTriggerLock)
                .putString(KEY_TARGET_RESOLUTION_STR,mTargetResolutionStr)
                .putString(KEY_TARGET_FPS, mTargetFPS)
                .putString(KEY_CAMERA_SELECT_MODE, Integer.toString(mCameraSelectMode))
                .putInt(KEY_BATTERY_OVERHEAT_TEMP, mBatteryOverheatTemp)
                .apply();
    }
}
