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

import io.crayfis.android.camera.CFCamera;
import io.crayfis.android.camera.ResolutionSpec;
import io.crayfis.android.trigger.L0.L0Processor;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.trigger.quality.QualityProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Global configuration class.
 */
public final class CFConfig implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final CFConfig INSTANCE = new CFConfig();

    private static final String KEY_L0_TRIGGER = "L0_trigger";
    private static final String KEY_QUAL_TRIGGER = "qual_trigger";
    private static final String KEY_PRECAL_TRIGGER = "precal_trigger";
    private static final String KEY_L1_TRIGGER = "L1_trigger";
    private static final String KEY_L2_TRIGGER = "L2_trigger";
    private static final String KEY_WEIGHTS = "precal_weights_";
    private static final String KEY_HOTCELLS = "hotcells_";
    private static final String KEY_PRECAL_MOST = "precal_uuid_most_";
    private static final String KEY_PRECAL_LEAST = "precal_uuid_least_";
    private static final String KEY_LAST_PRECAL_TIME = "last_precal_time_";
    private static final String KEY_LAST_PRECAL_RES_X = "last_precal_res_x_";
    private static final String KEY_XB_PERIOD = "xb_period";
    private static final String KEY_MAX_UPLOAD_INTERVAL = "max_upload_interval";
    private static final String KEY_MAX_CHUNK_SIZE = "max_chunk_size";
    private static final String KEY_CACHE_UPLOAD_INTERVAL = "min_cache_upload_interval";
    private static final String KEY_CURRENT_EXPERIMENT = "current_experiment";
    private static final String KEY_DEVICE_NICKNAME = "device_nickname";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_SCORE = "account_score";
    private static final String KEY_TARGET_RESOLUTION_STR = "prefResolution";
    private static final String KEY_TARGET_FPS = "prefFPS";
    private static final String KEY_BATTERY_OVERHEAT_TEMP = "battery_overheat_temp";
    private static final String KEY_PRECAL_RESET_TIME = "precal_reset_time";

    private final int N_CAMERAS;
    private static final String DEFAULT_L0_TRIGGER = "";
    private static final String DEFAULT_QUAL_TRIGGER = "";
    private static final String DEFAULT_PRECAL_TRIGGER = "";
    private static final String DEFAULT_L1_TRIGGER = "";
    private static final String DEFAULT_L2_TRIGGER = "";
    private static final Set<String> DEFAULT_HOTCELLS = new HashSet<>();
    private static final int DEFAULT_XB_PERIOD = 120;
    private static final int DEFAULT_MAX_UPLOAD_INTERVAL = 180;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 250000;
    private static final int DEFAULT_CACHE_UPLOAD_INTERVAL = 30;
    private static final String DEFAULT_CURRENT_EXPERIMENT = null;
    private static final String DEFAULT_DEVICE_NICKNAME = null;
    private static final String DEFAULT_ACCOUNT_NAME = null;
    private static final float DEFAULT_ACCOUNT_SCORE = (float)0.;
    private static final String DEFAULT_TARGET_RESOLUTION_STR = "1080p";
    private static final Float DEFAULT_TARGET_FPS = 30f;
    private static final int DEFAULT_BATTERY_OVERHEAT_TEMP = 410;
    private static final Long DEFAULT_PRECAL_RESET_TIME = 7*24*3600 * 1000L;

    private TriggerProcessor.Config mL0Trigger;
    private TriggerProcessor.Config mQualTrigger;
    private PreCalibrator.ConfigList mPrecalTriggers;
    private TriggerProcessor.Config mL1Trigger;
    private TriggerProcessor.Config mL2Trigger;
    private List<Set<String>> mHotcells;
    private String[] mPrecalWeights;
    private UUID[] mPrecalUUID;
    private long[] mLastPrecalTime;
    private int[] mLastPrecalResX;
    private int mExposureBlockPeriod;
    private int mMaxUploadInterval;
    private int mMaxChunkSize;
    private int mCacheUploadInterval;
    private String mCurrentExperiment;
    private String mDeviceNickname;
    private String mAccountName;
    private float mAccountScore;
    private String mTargetResolutionStr;
    private Float mTargetFPS;
    private int mBatteryOverheatTemp;
    private Long mPrecalResetTime; // ms

    private CFConfig() {
        // FIXME: shouldn't we initialize based on the persistent config values?
        N_CAMERAS = Camera.getNumberOfCameras();

        mL0Trigger = L0Processor.makeConfig(DEFAULT_L0_TRIGGER);
        mQualTrigger = QualityProcessor.makeConfig(DEFAULT_QUAL_TRIGGER);
        mPrecalTriggers = PreCalibrator.makeConfig(DEFAULT_PRECAL_TRIGGER);
        mL1Trigger = L1Processor.makeConfig(DEFAULT_L1_TRIGGER);
        mL2Trigger = L2Processor.makeConfig(DEFAULT_L2_TRIGGER);
        mExposureBlockPeriod = DEFAULT_XB_PERIOD;
        mMaxUploadInterval = DEFAULT_MAX_UPLOAD_INTERVAL;
        mMaxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        mCacheUploadInterval = DEFAULT_CACHE_UPLOAD_INTERVAL;
        mCurrentExperiment = DEFAULT_CURRENT_EXPERIMENT;
        mDeviceNickname = DEFAULT_DEVICE_NICKNAME;
        mAccountName = DEFAULT_ACCOUNT_NAME;
        mAccountScore = DEFAULT_ACCOUNT_SCORE;
        mTargetResolutionStr = DEFAULT_TARGET_RESOLUTION_STR;
        mTargetFPS = DEFAULT_TARGET_FPS;
        mBatteryOverheatTemp = DEFAULT_BATTERY_OVERHEAT_TEMP;
        mPrecalResetTime = DEFAULT_PRECAL_RESET_TIME;

        mHotcells = new ArrayList<>(N_CAMERAS);
        for(int i=0; i<N_CAMERAS; i++) {
            mHotcells.add(new HashSet<String>());
        }
    }

    public TriggerProcessor.Config getL0Trigger() {
        return mL0Trigger;
    }
    
    public TriggerProcessor.Config getQualTrigger() {
        return mQualTrigger;
    }

    public PreCalibrator.ConfigList getPrecalTrigger() {
        return mPrecalTriggers;
    }

    public TriggerProcessor.Config getL1Trigger() {
        return mL1Trigger;
    }

    public TriggerProcessor.Config getL2Trigger() {
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
    public Integer getL1Threshold() {
        return mL1Trigger.getInt(L1Processor.KEY_L1_THRESH);
    }

    /**
     * Set the threshold for camera frame capturing.
     *
     * @param l1Threshold The new threshold.
     */
    public void setL1Threshold(int l1Threshold) {
        mL1Trigger = mL1Trigger.edit()
                .putInt(L1Processor.KEY_L1_THRESH, l1Threshold)
                .create();
    }

    /**
     * Get the threshold for camera frame processing.
     *
     * @return int
     */
    public Integer getL2Threshold() {
        return mL2Trigger.getInt(L2Processor.KEY_L2_THRESH);
    }

    /**
     * Set the threshold for camera frame processing.
     *
     * @param l2Threshold The new threshold.
     */
    public void setL2Threshold(int l2Threshold) {
        mL2Trigger = mL2Trigger.edit()
                .putInt(L2Processor.KEY_L2_THRESH, l2Threshold)
                .create();
    }

    /**
     * How many frames to sample during calibration.  More frames is longer but gives better
     * statistics.
     *
     * @return int
     */
    public int getCalibrationSampleFrames() {
        return mL1Trigger.getInt(TriggerProcessor.Config.KEY_MAXFRAMES);
    }

    /**
     * Targeted max. number of events per minute to allow.  Lower rate => higher threshold.
     *
     * @return float
     */
    public float getTargetEventsPerMinute() {
        return mL1Trigger.getInt(L1Processor.KEY_TARGET_EPM);
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

    @Nullable
    public ResolutionSpec getTargetResolution() { return ResolutionSpec.fromString(mTargetResolutionStr); }

    public double getTargetFPS() {
        return mTargetFPS;
    }

    public int getBatteryOverheatTemp() {
        return mBatteryOverheatTemp;
    }

    public Long getPrecalResetTime() {
        return mPrecalResetTime;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL0Trigger = L0Processor.makeConfig(sharedPreferences.getString(KEY_L0_TRIGGER, DEFAULT_L0_TRIGGER));
        mQualTrigger = QualityProcessor.makeConfig(sharedPreferences.getString(KEY_QUAL_TRIGGER, DEFAULT_QUAL_TRIGGER));
        mPrecalTriggers = PreCalibrator.makeConfig(sharedPreferences.getString(KEY_PRECAL_TRIGGER, DEFAULT_PRECAL_TRIGGER));
        mL1Trigger = L1Processor.makeConfig(sharedPreferences.getString(KEY_L1_TRIGGER, DEFAULT_L1_TRIGGER));
        mL2Trigger = L2Processor.makeConfig(sharedPreferences.getString(KEY_L2_TRIGGER, DEFAULT_L2_TRIGGER));
        mExposureBlockPeriod = sharedPreferences.getInt(KEY_XB_PERIOD, DEFAULT_XB_PERIOD);
        mMaxUploadInterval = sharedPreferences.getInt(KEY_MAX_UPLOAD_INTERVAL, DEFAULT_MAX_UPLOAD_INTERVAL);
        mMaxChunkSize = sharedPreferences.getInt(KEY_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE);
        mCacheUploadInterval = sharedPreferences.getInt(KEY_CACHE_UPLOAD_INTERVAL, DEFAULT_CACHE_UPLOAD_INTERVAL);
        mCurrentExperiment = sharedPreferences.getString(KEY_CURRENT_EXPERIMENT, DEFAULT_CURRENT_EXPERIMENT);
        mDeviceNickname = sharedPreferences.getString(KEY_DEVICE_NICKNAME, DEFAULT_DEVICE_NICKNAME);
        mAccountName = sharedPreferences.getString(KEY_ACCOUNT_NAME, DEFAULT_ACCOUNT_NAME);
        mAccountScore = sharedPreferences.getFloat(KEY_ACCOUNT_SCORE, DEFAULT_ACCOUNT_SCORE);
        mTargetResolutionStr = sharedPreferences.getString(KEY_TARGET_RESOLUTION_STR, DEFAULT_TARGET_RESOLUTION_STR);
        try {
            mTargetFPS = Float.parseFloat(sharedPreferences.getString(KEY_TARGET_FPS, DEFAULT_TARGET_FPS.toString()));
        } catch (NumberFormatException e) {
            mTargetFPS = DEFAULT_TARGET_FPS;
        }

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
        if (serverCommand.getL0Trigger() != null) {
            mL0Trigger = L0Processor.makeConfig(serverCommand.getL0Trigger());
        }
        if (serverCommand.getQualityTrigger() != null) {
            mQualTrigger = QualityProcessor.makeConfig(serverCommand.getQualityTrigger());
        }
        if (serverCommand.getPrecalTrigger() != null) {
            mPrecalTriggers = PreCalibrator.makeConfig(serverCommand.getPrecalTrigger());
        }
        if (serverCommand.getL1Trigger() != null) {
            mL1Trigger = L1Processor.makeConfig(serverCommand.getL1Trigger());
        }
        if (serverCommand.getL2Trigger() != null) {
            mL2Trigger = L2Processor.makeConfig(serverCommand.getL2Trigger());
        }
        if (serverCommand.getTargetExposureBlockPeriod() != null) {
            mExposureBlockPeriod = serverCommand.getTargetExposureBlockPeriod();
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
        // if we're changing the camera settings, reconfigure it
        if (serverCommand.getResolution() != null) {
            mTargetResolutionStr = serverCommand.getResolution();
            CFCamera.getInstance().changeCamera();
        }
        if(serverCommand.getTargetFPS() != null) {
            mTargetFPS = serverCommand.getTargetFPS();
            CFCamera.getInstance().changeCamera();
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

        editor.putString(KEY_L0_TRIGGER, mL0Trigger.toString())
                .putString(KEY_QUAL_TRIGGER, mQualTrigger.toString())
                .putString(KEY_PRECAL_TRIGGER, mPrecalTriggers.toString())
                .putString(KEY_L1_TRIGGER, mL1Trigger.toString())
                .putString(KEY_L2_TRIGGER, mL2Trigger.toString())
                .putInt(KEY_XB_PERIOD, mExposureBlockPeriod)
                .putInt(KEY_MAX_UPLOAD_INTERVAL, mMaxUploadInterval)
                .putInt(KEY_MAX_CHUNK_SIZE, mMaxChunkSize)
                .putString(KEY_CURRENT_EXPERIMENT, mCurrentExperiment)
                .putString(KEY_DEVICE_NICKNAME, mDeviceNickname)
                .putString(KEY_ACCOUNT_NAME,mAccountName)
                .putFloat(KEY_ACCOUNT_SCORE,mAccountScore)
                .putString(KEY_TARGET_RESOLUTION_STR,mTargetResolutionStr)
                .putString(KEY_TARGET_FPS, mTargetFPS.toString())
                .putInt(KEY_BATTERY_OVERHEAT_TEMP, mBatteryOverheatTemp)
                .apply();
    }
}
