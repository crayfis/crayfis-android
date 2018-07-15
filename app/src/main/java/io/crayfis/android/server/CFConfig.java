package io.crayfis.android.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.preference.PreferenceManager;
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
    private static final String KEY_XB_PERIOD = "xb_period";
    private static final String KEY_CURRENT_EXPERIMENT = "current_experiment";
    private static final String KEY_DEVICE_NICKNAME = "device_nickname";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_SCORE = "account_score";
    private static final String KEY_TARGET_RESOLUTION_STR = "prefResolution";
    private static final String KEY_TARGET_FPS = "prefFPS";
    private static final String KEY_FRAC_DEAD_TIME = "prefDeadTime";
    private static final String KEY_BATTERY_OVERHEAT_TEMP = "battery_overheat_temp";

    private static final String DEFAULT_L0_TRIGGER = "";
    private static final String DEFAULT_QUAL_TRIGGER = "";
    private static final String DEFAULT_PRECAL_TRIGGER = "";
    private static final String DEFAULT_L1_TRIGGER = "";
    private static final String DEFAULT_L2_TRIGGER = "";
    private static final int DEFAULT_XB_PERIOD = 120;
    private static final String DEFAULT_CURRENT_EXPERIMENT = null;
    private static final String DEFAULT_DEVICE_NICKNAME = null;
    private static final String DEFAULT_ACCOUNT_NAME = null;
    private static final float DEFAULT_ACCOUNT_SCORE = (float)0.;
    private static final String DEFAULT_TARGET_RESOLUTION_STR = "1080p";
    private static final Float DEFAULT_TARGET_FPS = 30f;
    private static final float DEFAULT_FRAC_DEAD_TIME = .01f;
    private static final int DEFAULT_BATTERY_OVERHEAT_TEMP = 410;

    private TriggerProcessor.Config mL0Trigger;
    private TriggerProcessor.Config mQualTrigger;
    private PreCalibrator.ConfigList mPrecalTriggers;
    private TriggerProcessor.Config mL1Trigger;
    private TriggerProcessor.Config mL2Trigger;
    private int mExposureBlockPeriod;
    private String mCurrentExperiment;
    private String mDeviceNickname;
    private String mAccountName;
    private float mAccountScore;
    private String mTargetResolutionStr;
    private Float mTargetFPS;
    private float mFracDeadTime;
    private int mBatteryOverheatTemp;

    private CFConfig() {
        mL0Trigger = L0Processor.makeConfig(DEFAULT_L0_TRIGGER);
        mQualTrigger = QualityProcessor.makeConfig(DEFAULT_QUAL_TRIGGER);
        mPrecalTriggers = PreCalibrator.makeConfig(DEFAULT_PRECAL_TRIGGER);
        mL1Trigger = L1Processor.makeConfig(DEFAULT_L1_TRIGGER);
        mL2Trigger = L2Processor.makeConfig(DEFAULT_L2_TRIGGER);
        mExposureBlockPeriod = DEFAULT_XB_PERIOD;
        mCurrentExperiment = DEFAULT_CURRENT_EXPERIMENT;
        mDeviceNickname = DEFAULT_DEVICE_NICKNAME;
        mAccountName = DEFAULT_ACCOUNT_NAME;
        mAccountScore = DEFAULT_ACCOUNT_SCORE;
        mTargetResolutionStr = DEFAULT_TARGET_RESOLUTION_STR;
        mTargetFPS = DEFAULT_TARGET_FPS;
        mFracDeadTime = DEFAULT_FRAC_DEAD_TIME;
        mBatteryOverheatTemp = DEFAULT_BATTERY_OVERHEAT_TEMP;
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

    public void setAccountName(@Nullable final String accountName) {
        mAccountName = accountName;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public float getAccountScore() { return mAccountScore; }

    public void setTargetResolution(int resX, int resY) {
        mTargetResolutionStr = new ResolutionSpec(resX, resY).toString();
    }

    @Nullable
    public ResolutionSpec getTargetResolution() { return ResolutionSpec.fromString(mTargetResolutionStr); }

    public double getTargetFPS() {
        return mTargetFPS;
    }

    public double getFracDeadTime() {
        return mFracDeadTime;
    }

    public int getBatteryOverheatTemp() {
        return mBatteryOverheatTemp;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL0Trigger = L0Processor.makeConfig(sharedPreferences.getString(KEY_L0_TRIGGER, DEFAULT_L0_TRIGGER));
        mQualTrigger = QualityProcessor.makeConfig(sharedPreferences.getString(KEY_QUAL_TRIGGER, DEFAULT_QUAL_TRIGGER));
        mPrecalTriggers = PreCalibrator.makeConfig(sharedPreferences.getString(KEY_PRECAL_TRIGGER, DEFAULT_PRECAL_TRIGGER));
        mL1Trigger = L1Processor.makeConfig(sharedPreferences.getString(KEY_L1_TRIGGER, DEFAULT_L1_TRIGGER));
        mL2Trigger = L2Processor.makeConfig(sharedPreferences.getString(KEY_L2_TRIGGER, DEFAULT_L2_TRIGGER));
        mExposureBlockPeriod = sharedPreferences.getInt(KEY_XB_PERIOD, DEFAULT_XB_PERIOD);
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

    }

    /**
     * Update configuration based on commands from the server.
     *
     * @param serverCommand {@link io.crayfis.android.server.ServerCommand}
     */
    void updateFromServer(@NonNull final ServerCommand serverCommand) {

        CFLog.i("GOT command from server!");
        boolean changeCamera = false;

        if (serverCommand.getUpdateCommand() != null
                && serverCommand.getUpdateCommand().isApplicable()) {
            changeCamera = true;
        }
        if (serverCommand.getCameraCommand() != null
                && serverCommand.getCameraCommand().isApplicable()) {
            CFCamera.getInstance().changeDataRate(serverCommand.getCameraCommand().shouldIncrease());
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
        if (serverCommand.getAccountName() != null) {
            mAccountName = serverCommand.getAccountName();
        }
        if (serverCommand.getAccountScore() != null) {
            mAccountScore = serverCommand.getAccountScore();
        }
        // if we're changing the camera settings, reconfigure it
        if (serverCommand.getResolution() != null) {
            mTargetResolutionStr = serverCommand.getResolution();
            changeCamera = true;
        }
        if(serverCommand.getTargetFPS() != null) {
            mTargetFPS = serverCommand.getTargetFPS();
            changeCamera = true;
        }
        if(serverCommand.getFracDeadTime() != null) {
            mFracDeadTime = serverCommand.getFracDeadTime();
            changeCamera = true;
        }
        if (serverCommand.getBatteryOverheatTemp() != null) {
            mBatteryOverheatTemp = serverCommand.getBatteryOverheatTemp();
        }
        if (serverCommand.shouldRecalibrate() != null) {
            changeCamera = true;
        }

        if (changeCamera) {
            CFCamera.getInstance().changeCamera();
        }
    }

    public void save(@NonNull final SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(KEY_L0_TRIGGER, mL0Trigger.toString())
                .putString(KEY_QUAL_TRIGGER, mQualTrigger.toString())
                .putString(KEY_PRECAL_TRIGGER, mPrecalTriggers.toString())
                .putString(KEY_L1_TRIGGER, mL1Trigger.toString())
                .putString(KEY_L2_TRIGGER, mL2Trigger.toString())
                .putInt(KEY_XB_PERIOD, mExposureBlockPeriod)
                .putString(KEY_CURRENT_EXPERIMENT, mCurrentExperiment)
                .putString(KEY_DEVICE_NICKNAME, mDeviceNickname)
                .putString(KEY_ACCOUNT_NAME,mAccountName)
                .putFloat(KEY_ACCOUNT_SCORE,mAccountScore)
                .putString(KEY_TARGET_RESOLUTION_STR,mTargetResolutionStr)
                .putString(KEY_TARGET_FPS, mTargetFPS.toString())
                .putFloat(KEY_FRAC_DEAD_TIME, mFracDeadTime)
                .putInt(KEY_BATTERY_OVERHEAT_TEMP, mBatteryOverheatTemp)
                .apply();
    }
}
