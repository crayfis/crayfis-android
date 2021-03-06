package io.crayfis.android.server;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.crayfis.android.BuildConfig;
import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.daq.ResolutionSpec;
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

    // load secret salt
    static {
        if(BuildConfig.DEBUG) {
            System.loadLibrary("debug-lib");
        } else {
            System.loadLibrary("release-lib");
        }
    }
    public static native String getSecretSalt();

    private static final CFConfig INSTANCE = new CFConfig();

    private static final String KEY_L0_TRIGGER = "L0_trigger";
    private static final String KEY_QUAL_TRIGGER = "qual_trigger";
    private static final String KEY_PRECAL_TRIGGER = "precal_trigger";
    private static final String KEY_L1_TRIGGER = "L1_trigger";
    private static final String KEY_L2_TRIGGER = "L2_trigger";
    private static final String KEY_XB_TARGET_EVENTS = "xb_target_events";
    private static final String KEY_CURRENT_EXPERIMENT = "current_experiment";
    private static final String KEY_DEVICE_NICKNAME = "device_nickname";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_SCORE = "account_score";
    private static final String KEY_TARGET_RESOLUTION_STR = "prefResolution";
    private static final String KEY_TARGET_FPS = "prefFPS";
    private static final String KEY_N_ALLOC = "nAlloc";
    private static final String KEY_FRAC_DEAD_TIME = "prefDeadTime";
    private static final String KEY_BATTERY_OVERHEAT_TEMP = "battery_overheat_temp";
    private static final String KEY_DATACHUNK_SIZE = "datachunk_size";

    private static final String DEFAULT_L0_TRIGGER = "";
    private static final String DEFAULT_QUAL_TRIGGER = "";
    private static final String DEFAULT_PRECAL_TRIGGER = "";
    private static final String DEFAULT_L1_TRIGGER = "";
    private static final String DEFAULT_L2_TRIGGER = "";
    private static final int DEFAULT_XB_TARGET_EVENTS = 60;
    private static final String DEFAULT_CURRENT_EXPERIMENT = null;
    private static final String DEFAULT_DEVICE_NICKNAME = null;
    private static final String DEFAULT_ACCOUNT_NAME = null;
    private static final float DEFAULT_ACCOUNT_SCORE = (float)0.;
    private static final PreCalibrationService.Config DEFAULT_PRECAL_CONFIG = null;
    private static final String DEFAULT_TARGET_RESOLUTION_STR = "1080p";
    private static final Float DEFAULT_TARGET_FPS = 30f;
    private static final int DEFAULT_ISO_GAIN = 0;
    private static final int DEFAULT_N_ALLOC = 2;
    private static final float DEFAULT_FRAC_DEAD_TIME = .01f;
    private static final int DEFAULT_BATTERY_OVERHEAT_TEMP = 410;
    private static final long DEFAULT_DATACHUNK_SIZE = 50000L;

    private TriggerProcessor.Config mL0Trigger;
    private TriggerProcessor.Config mQualTrigger;
    private PreCalibrator.ConfigList mPrecalTriggers;
    private TriggerProcessor.Config mL1Trigger;
    private TriggerProcessor.Config mL2Trigger;
    private boolean mThresholdsSet;
    private int mExposureBlockTargetEvents;
    private String mCurrentExperiment;
    private String mDeviceNickname;
    private String mAccountName;
    private float mAccountScore;
    private PreCalibrationService.Config mPrecalConfig;
    private String mTargetResolutionStr;
    private Float mTargetFPS;
    private int mISOGain;
    private int mNAlloc;
    private float mFracDeadTime;
    private int mBatteryOverheatTemp;
    private long mDataChunkSize;

    private CFConfig() {
        mL0Trigger = L0Processor.makeConfig(DEFAULT_L0_TRIGGER);
        mQualTrigger = QualityProcessor.makeConfig(DEFAULT_QUAL_TRIGGER);
        mPrecalTriggers = PreCalibrator.makeConfig(DEFAULT_PRECAL_TRIGGER);
        mL1Trigger = L1Processor.makeConfig(DEFAULT_L1_TRIGGER);
        mL2Trigger = L2Processor.makeConfig(DEFAULT_L2_TRIGGER);
        mThresholdsSet = false;
        mExposureBlockTargetEvents = DEFAULT_XB_TARGET_EVENTS;
        mCurrentExperiment = DEFAULT_CURRENT_EXPERIMENT;
        mDeviceNickname = DEFAULT_DEVICE_NICKNAME;
        mAccountName = DEFAULT_ACCOUNT_NAME;
        mAccountScore = DEFAULT_ACCOUNT_SCORE;
        mPrecalConfig = DEFAULT_PRECAL_CONFIG;
        mTargetResolutionStr = DEFAULT_TARGET_RESOLUTION_STR;
        mTargetFPS = DEFAULT_TARGET_FPS;
        mISOGain = DEFAULT_ISO_GAIN;
        mNAlloc = DEFAULT_N_ALLOC;
        mFracDeadTime = DEFAULT_FRAC_DEAD_TIME;
        mBatteryOverheatTemp = DEFAULT_BATTERY_OVERHEAT_TEMP;
        mDataChunkSize = DEFAULT_DATACHUNK_SIZE;
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

        // set the threshold at max if we haven't calibrated it yet
        if(!mThresholdsSet) {

            float threshMax = DAQManager.getInstance().isStreamingRAW() ? 1023f : 255f;
            mL1Trigger = mL1Trigger.edit()
                    .putFloat(L1Processor.KEY_L1_THRESH, threshMax)
                    .create();

        }
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
    public Float getL1Threshold() {
        return getL1Trigger().getFloat(L1Processor.KEY_L1_THRESH);
    }

    /**
     * Set the thresholds for camera frame capturing.
     *
     * @param l1Thresh New L1 threshold
     * @param l2Thresh New L2 threshold
     */
    public void setThresholds(float l1Thresh, int l2Thresh) {
        mThresholdsSet = true;
        mL1Trigger = mL1Trigger.edit()
                .putFloat(L1Processor.KEY_L1_THRESH, l1Thresh)
                .create();
        mL2Trigger = mL2Trigger.edit()
                .putInt(L2Processor.KEY_L2_THRESH, l2Thresh)
                .create();
    }

    /**
     * Set the L1 threshold and use an automatically calculated L2
     *
     * @param l1Thresh New L1 threshold.  If null, unset thresholds and return
     *                 maximum threshold value
     */
    public void setThresholds(@Nullable Float l1Thresh) {
        if(l1Thresh == null) {
            mThresholdsSet = false;
            return;
        }
        int l2Thresh = ((L2Processor.Config)mL2Trigger).generateL2Threshold(l1Thresh);
        setThresholds(l1Thresh, l2Thresh);
    }

    /**
     * For locked thresholds, mark as set for DATA state
     */
    public void setThresholds() {
        mThresholdsSet = true;
    }

    /**
     * How many frames to sample during calibration.  More frames is longer but gives better
     * statistics.
     *
     * @return int
     */
    public int getCalibrationSampleFrames() {
        return getL1Trigger().getInt(TriggerProcessor.Config.KEY_MAXFRAMES);
    }

    /**
     * Targeted max. number of events per minute to allow.  Lower rate => higher threshold.
     *
     * @return float
     */
    public float getTargetEventsPerMinute() {
        return getL1Trigger().getFloat(L1Processor.KEY_TARGET_EPM);
    }

    /**
     * The nominal period for an exposure block (in seconds)
     *
     * @return int
     */
    public int getExposureBlockPeriod() {
        return (int) (60 * mExposureBlockTargetEvents / getTargetEventsPerMinute());
    }

    public int getExposureBlockTargetEvents() {
        return mExposureBlockTargetEvents;
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

    public void setPrecalConfig(PreCalibrationService.Config config) {
        mPrecalConfig = config;
    }

    public PreCalibrationService.Config getPrecalConfig() {
        return mPrecalConfig;
    }

    public void setTargetResolution(int resX, int resY) {
        mTargetResolutionStr = new ResolutionSpec(resX, resY).toString();
    }

    public void setTargetResolution(ResolutionSpec res) {
        mTargetResolutionStr = res.toString();
    }

    @Nullable
    public ResolutionSpec getTargetResolution() { return ResolutionSpec.fromString(mTargetResolutionStr); }

    public double getTargetFPS() {
        if(mTargetFPS == null) mTargetFPS = DEFAULT_TARGET_FPS;
        return mTargetFPS;
    }

    public int getISOGain() {
        return mISOGain;
    }

    public int getNAlloc() {
        return mNAlloc;
    }

    public double getFracDeadTime() {
        return mFracDeadTime;
    }

    public int getBatteryOverheatTemp() {
        return mBatteryOverheatTemp;
    }

    public long getDataChunkSize() {
        return mDataChunkSize;
    }

    /**
     * Update configuration based on commands from the server.
     *
     * @param serverCommand {@link io.crayfis.android.server.ServerCommand}
     */
    void updateFromServer(@NonNull final ServerCommand serverCommand) {

        CFLog.i("GOT command from server!");
        boolean restartCamera = false;
        boolean recalibrate = false;

        if (serverCommand.getWeights() != null
                && serverCommand.getWeights().isApplicable()) {
            PreCalibrationService.Config newConfig = serverCommand.getWeights().makePrecalConfig();
            if(mPrecalConfig == null)
                mPrecalConfig = newConfig;
            else
                mPrecalConfig = mPrecalConfig.update(newConfig);
        }
        if (serverCommand.getHotcells() != null
                && serverCommand.getHotcells().isApplicable()) {
            PreCalibrationService.Config newConfig = serverCommand.getHotcells().makePrecalConfig();
            if(mPrecalConfig == null)
                mPrecalConfig = newConfig;
            else
                mPrecalConfig = mPrecalConfig.update(newConfig);
        }
        // only call PreCalibrationService if the commands above don't get the job done
        if (serverCommand.getUpdateCommand() != null
                && serverCommand.getUpdateCommand().isApplicable()
                && serverCommand.getWeights() == null
                && serverCommand.getHotcells() == null) {
            recalibrate = true;
        }
        if (serverCommand.getCameraCommand() != null
                && serverCommand.getCameraCommand().isApplicable()) {
            DAQManager.getInstance().changeDataRate(serverCommand.getCameraCommand().shouldIncrease());
            restartCamera = true;
        }
        if (serverCommand.getL0Trigger() != null) {
            if(serverCommand.getL0Trigger().hasName()) {
                mL0Trigger = L0Processor.makeConfig(serverCommand.getL0Trigger().toString());
            } else if(mL0Trigger != null) {
                mL0Trigger = mL0Trigger.editFromString(serverCommand.getL0Trigger().toString());
            }
        }
        if (serverCommand.getQualityTrigger() != null) {
            if(serverCommand.getQualityTrigger().hasName()) {
                mQualTrigger = QualityProcessor.makeConfig(serverCommand.getQualityTrigger().toString());
            } else if(mQualTrigger != null) {
                mQualTrigger = mQualTrigger.editFromString(serverCommand.getQualityTrigger().toString());
            }
        }
        if (serverCommand.getPrecalTriggers() != null) {
            StringBuilder sb = new StringBuilder();
            for(int i=0; i<serverCommand.getPrecalTriggers().length; i++) {
                ServerCommand.TrigCommand cmd = serverCommand.getPrecalTriggers()[i];
                if(i>0) {
                    sb.append("->");
                }
                sb.append(cmd.toString());
            }
            mPrecalTriggers = PreCalibrator.makeConfig(sb.toString());
        }
        if (serverCommand.getL1Trigger() != null) {
            if(serverCommand.getL1Trigger().hasName()) {
                mL1Trigger = L1Processor.makeConfig(serverCommand.getL1Trigger().toString());
            } else if(mL1Trigger != null) {
                mL1Trigger = mL1Trigger.editFromString(serverCommand.getL1Trigger().toString());
            }
        }
        if (serverCommand.getL2Trigger() != null) {
            if(serverCommand.getL2Trigger().hasName()) {
                mL2Trigger = L2Processor.makeConfig(serverCommand.getL2Trigger().toString());
            } else if(mL2Trigger != null) {
                mL2Trigger = mL2Trigger.editFromString(serverCommand.getL2Trigger().toString());
            }
        }
        if (serverCommand.getTargetExposureBlockPeriod() != null) {
            mExposureBlockTargetEvents = (int)(serverCommand.getTargetExposureBlockPeriod()
                    * getL1Trigger().getFloat(L1Processor.KEY_TARGET_EPM) / 60);
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
            restartCamera = true;
        }
        if(serverCommand.getTargetFPS() != null) {
            mTargetFPS = serverCommand.getTargetFPS();
            restartCamera = true;
        }
        if(serverCommand.getISOGain() != null) {
            mISOGain = serverCommand.getISOGain();
            restartCamera = true;
        }
        if(serverCommand.getNAlloc() != null) {
            mNAlloc = serverCommand.getNAlloc();
            restartCamera = true;
        }
        if(serverCommand.getFracDeadTime() != null) {
            mFracDeadTime = serverCommand.getFracDeadTime();
            restartCamera = true;
        }
        if (serverCommand.getBatteryOverheatTemp() != null) {
            mBatteryOverheatTemp = serverCommand.getBatteryOverheatTemp();
        }
        if (serverCommand.getDataChunkSize() != null) {
            mDataChunkSize = serverCommand.getDataChunkSize();
        }
        if (serverCommand.shouldRecalibrate() != null) {
            recalibrate = true;
        }

        // avoid duplicating operations
        if (restartCamera) {
            DAQManager.getInstance().reconfigureCamera();
        } else if(recalibrate) {
            DAQManager.getInstance().recalibrate();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mL0Trigger = L0Processor.makeConfig(sharedPreferences.getString(KEY_L0_TRIGGER, DEFAULT_L0_TRIGGER));
        mQualTrigger = QualityProcessor.makeConfig(sharedPreferences.getString(KEY_QUAL_TRIGGER, DEFAULT_QUAL_TRIGGER));
        mPrecalTriggers = PreCalibrator.makeConfig(sharedPreferences.getString(KEY_PRECAL_TRIGGER, DEFAULT_PRECAL_TRIGGER));
        mL1Trigger = L1Processor.makeConfig(sharedPreferences.getString(KEY_L1_TRIGGER, DEFAULT_L1_TRIGGER));
        mL2Trigger = L2Processor.makeConfig(sharedPreferences.getString(KEY_L2_TRIGGER, DEFAULT_L2_TRIGGER));
        mExposureBlockTargetEvents = sharedPreferences.getInt(KEY_XB_TARGET_EVENTS, DEFAULT_XB_TARGET_EVENTS);
        mCurrentExperiment = sharedPreferences.getString(KEY_CURRENT_EXPERIMENT, DEFAULT_CURRENT_EXPERIMENT);
        mDeviceNickname = sharedPreferences.getString(KEY_DEVICE_NICKNAME, DEFAULT_DEVICE_NICKNAME);
        mAccountName = sharedPreferences.getString(KEY_ACCOUNT_NAME, DEFAULT_ACCOUNT_NAME);
        mAccountScore = sharedPreferences.getFloat(KEY_ACCOUNT_SCORE, DEFAULT_ACCOUNT_SCORE);
        mTargetResolutionStr = sharedPreferences.getString(KEY_TARGET_RESOLUTION_STR, DEFAULT_TARGET_RESOLUTION_STR);
        // this is necessary to make this configurable in the settings
        try {
            mTargetFPS = Float.parseFloat(sharedPreferences.getString(KEY_TARGET_FPS, DEFAULT_TARGET_FPS.toString()));
        } catch (NumberFormatException e) {
            mTargetFPS = DEFAULT_TARGET_FPS;
        }
        mNAlloc = sharedPreferences.getInt(KEY_N_ALLOC, DEFAULT_N_ALLOC);
        mFracDeadTime = sharedPreferences.getFloat(KEY_FRAC_DEAD_TIME, DEFAULT_FRAC_DEAD_TIME);
        mBatteryOverheatTemp = sharedPreferences.getInt(KEY_BATTERY_OVERHEAT_TEMP, DEFAULT_BATTERY_OVERHEAT_TEMP);
        mDataChunkSize = sharedPreferences.getLong(KEY_DATACHUNK_SIZE, DEFAULT_DATACHUNK_SIZE);
    }

    public void save(@NonNull final SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(KEY_L0_TRIGGER, mL0Trigger.toString())
                .putString(KEY_QUAL_TRIGGER, mQualTrigger.toString())
                .putString(KEY_PRECAL_TRIGGER, mPrecalTriggers.toString())
                .putString(KEY_L1_TRIGGER, getL1Trigger().toString())
                .putString(KEY_L2_TRIGGER, getL2Trigger().toString())
                .putInt(KEY_XB_TARGET_EVENTS, mExposureBlockTargetEvents)
                .putString(KEY_CURRENT_EXPERIMENT, mCurrentExperiment)
                .putString(KEY_DEVICE_NICKNAME, mDeviceNickname)
                .putString(KEY_ACCOUNT_NAME,mAccountName)
                .putFloat(KEY_ACCOUNT_SCORE,mAccountScore)
                .putString(KEY_TARGET_RESOLUTION_STR,mTargetResolutionStr)
                .putString(KEY_TARGET_FPS, mTargetFPS.toString())
                .putInt(KEY_N_ALLOC, mNAlloc)
                .putFloat(KEY_FRAC_DEAD_TIME, mFracDeadTime)
                .putInt(KEY_BATTERY_OVERHEAT_TEMP, mBatteryOverheatTemp)
                .putLong(KEY_DATACHUNK_SIZE, mDataChunkSize)
                .apply();

        if(mPrecalConfig != null) mPrecalConfig.saveToPrefs(sharedPreferences);
    }
}
