package io.crayfis.android.server;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import io.crayfis.android.daq.DAQManager;
import io.crayfis.android.trigger.L0.L0Processor;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.trigger.quality.QualityProcessor;
import io.crayfis.android.util.CFLog;

/**
 * Wrapper class for parsing a server command response.  This uses objects rather than primitives
 * because I'm not sure what the valid value ranges are, 0 may be valid, same with negative numbers.
 * Getters return {@code null} when their values have not been set.
 *
 * TODO This is based on the assumption that the JSON response only has fields that should change.  Is this really true?
 */
class ServerCommand {

    @SerializedName("set_weights") private PrecalCommand mWeights;
    @SerializedName("set_hotcells") private PrecalCommand mHotcells;
    @SerializedName("update_precal") private PrecalCommand mUpdate;
    @SerializedName("change_data_rate") private CameraCommand mCameraCommand;
    @SerializedName("set_L0_trig") private L0TrigCommand mL0Trigger;
    @SerializedName("set_qual_trig") private QualTrigCommand mQualityTrigger;
    @SerializedName("set_precal_trig") private PrecalTrigCommand[] mPrecalTriggers;
    @SerializedName("set_L1_trig") private L1TrigCommand mL1Trigger;
    @SerializedName("set_L2_trig") private L2TrigCommand mL2Trigger;
    @SerializedName("set_xb_period") private Integer mTargetExposureBlockPeriod;
    @SerializedName("cmd_recalibrate") private Boolean mShouldRecalibrate;
    @SerializedName("experiment") private String mCurrentExperiment;
    @SerializedName("nickname") private String mDeviceNickname;
    @SerializedName("account_name") private String mAccountName;
    @SerializedName("account_score") private Float mAccountScore;
    @SerializedName("set_target_resolution") private String mResolution;
    @SerializedName("set_target_fps") private Float mTargetFPS;
    @SerializedName("set_n_alloc") private Integer mNAlloc;
    @SerializedName("set_frac_dead_time") private Float mFracDeadTime;
    @SerializedName("set_battery_overheat_temp") private Integer mBatteryOverheatTemp;
    @SerializedName("set_datachunk_size") private Long mDataChunkSize;

    class PrecalCommand {
        @SerializedName("camera_id") private Integer mCameraId;
        @SerializedName("res_x") private Integer mResX;
        @SerializedName("res_y") private Integer mResY;
        @SerializedName("mask") private int[] mHotcells;
        @SerializedName("hot_hash") private Integer mHotHash;
        @SerializedName("weights") private String mWeights;
        @SerializedName("wgt_hash") private Integer mWeightHash;

        boolean isApplicable() {
            DAQManager daq = DAQManager.getInstance();
            PreCalibrationService.Config cfg = CFConfig.getInstance().getPrecalConfig();
            return mCameraId == daq.getCameraId()
                    && mResX == daq.getResX()
                    && mResY == daq.getResY()
                    && (mHotHash != null && mHotHash != cfg.getHotHash()
                    || mWeightHash != null && mWeightHash != cfg.getWeightHash());
        }

        public PreCalibrationService.Config makePrecalConfig() {
            int hotHash = mHotHash == null ? -1 : mHotHash;
            int weightHash = mWeightHash == null ? -1 : mWeightHash;
            return new PreCalibrationService.Config(mCameraId, mResX, mResY, mHotcells, hotHash, mWeights, weightHash);
        }
    }

    class CameraCommand {
        @SerializedName("res_x") private Integer mResX;
        @SerializedName("res_y") private Integer mResY;
        @SerializedName("fps") private Float mFPS;
        @SerializedName("increase") private Boolean mShouldIncrease;

        boolean isApplicable() {
            DAQManager daq = DAQManager.getInstance();
            return mResX == daq.getResX()
                    && mResY == daq.getResY()
                    && mFPS == daq.getFPS()
                    && mShouldIncrease != null;
        }

        @Nullable
        Boolean shouldIncrease() {
            return mShouldIncrease;
        }
    }

    abstract class TrigCommand {
        @SerializedName("name") String mName;

        public boolean hasName() {
            return mName != null;
        }

        @Override
        @CallSuper
        public String toString() {
            return mName + ";";
        }
    }

    private class L0TrigCommand extends TrigCommand {
        @SerializedName(L0Processor.KEY_PRESCALE) private Float mPrescale;
        @SerializedName(L0Processor.KEY_RANDOM) private Boolean mRandom;
        @SerializedName(L0Processor.KEY_WINDOWSIZE) private Integer mWindowSize;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            if(mPrescale != null) sb.append(L0Processor.KEY_PRESCALE + "=" + mPrescale + ";");
            if(mRandom != null) sb.append(L0Processor.KEY_RANDOM + "=" + mRandom + ";");
            if(mWindowSize != null) sb.append(L0Processor.KEY_WINDOWSIZE + "=" + mWindowSize + ";");
            return sb.toString();
        }
    }

    private class QualTrigCommand extends TrigCommand {
        @SerializedName(TriggerProcessor.Config.KEY_MAXFRAMES) private Integer mMaxFrames;
        @SerializedName(QualityProcessor.KEY_BACKLOCK) private Boolean mBackLock;
        @SerializedName(QualityProcessor.KEY_MEAN_THRESH) private Float mMeanThresh;
        @SerializedName(QualityProcessor.KEY_ST_DEV_THRESH) private Float mStdThresh;
        @SerializedName(QualityProcessor.KEY_ORIENT_THRESH) private Float mOrientThresh;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            if(mMaxFrames != null) sb.append(TriggerProcessor.Config.KEY_MAXFRAMES + "=" + mMaxFrames + ";");
            if(mBackLock != null) sb.append(QualityProcessor.KEY_BACKLOCK + "=" + mBackLock + ";");
            if(mMeanThresh != null) sb.append(QualityProcessor.KEY_MEAN_THRESH + "=" + mMeanThresh + ";");
            if(mStdThresh != null) sb.append(QualityProcessor.KEY_ST_DEV_THRESH + "=" + mStdThresh + ";");
            if(mOrientThresh != null) sb.append(QualityProcessor.KEY_ORIENT_THRESH + "=" + mOrientThresh + ";");
            return sb.toString();
        }
    }

    private class PrecalTrigCommand extends TrigCommand {
        @SerializedName(TriggerProcessor.Config.KEY_MAXFRAMES) private Integer mMaxFrames;
        @SerializedName(PreCalibrator.KEY_HOTCELL_LIMIT) private Float mHotcellThresh;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            if(mMaxFrames != null) sb.append(TriggerProcessor.Config.KEY_MAXFRAMES + "=" + mMaxFrames + ";");
            if(mHotcellThresh != null) sb.append(PreCalibrator.KEY_HOTCELL_LIMIT + "=" + mHotcellThresh + ";");
            return sb.toString();
        }
    }

    private class L1TrigCommand extends TrigCommand {
        @SerializedName(TriggerProcessor.Config.KEY_MAXFRAMES) private Integer mMaxFrames;
        @SerializedName(L1Processor.KEY_L1_THRESH) private Float mL1Thresh;
        @SerializedName(L1Processor.KEY_TARGET_EPM) private Float mTargetEpm;
        @SerializedName(L1Processor.KEY_TRIGGER_LOCK) private Boolean mTriggerLock;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            if(mMaxFrames != null) sb.append(TriggerProcessor.Config.KEY_MAXFRAMES + "=" + mMaxFrames + ";");
            if(mL1Thresh != null) sb.append(L1Processor.KEY_L1_THRESH + "=" + mL1Thresh + ";");
            if(mTargetEpm != null) sb.append(L1Processor.KEY_TARGET_EPM + "=" + mTargetEpm + ";");
            if(mTriggerLock != null) sb.append(L1Processor.KEY_TRIGGER_LOCK + "=" + mTriggerLock + ";");
            return sb.toString();
        }
    }

    private class L2TrigCommand extends TrigCommand {
        @SerializedName(L2Processor.KEY_L2_THRESH) private Integer mL2Thresh;
        @SerializedName(L2Processor.KEY_NPIX) private Integer mNPix;
        @SerializedName(L2Processor.KEY_RADIUS) private Integer mRadius;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            if(mL2Thresh != null) sb.append(L2Processor.KEY_L2_THRESH + "=" + mL2Thresh + ";");
            if(mNPix != null) sb.append(L2Processor.KEY_NPIX + "=" + mNPix + ";");
            if(mRadius != null) sb.append(L2Processor.KEY_RADIUS + "=" + mRadius + ";");
            return sb.toString();
        }
    }

    @Nullable
    PrecalCommand getWeights() {
        return mWeights;
    }

    @Nullable
    PrecalCommand getHotcells() {
        return mHotcells;
    }


    @Nullable
    PrecalCommand getUpdateCommand() {
        return mUpdate;
    }

    @Nullable
    CameraCommand getCameraCommand() {
        return mCameraCommand;
    }


    /**
     * Get the Account Score
     *
     * @return Long or {@code null}.
     */
    @Nullable
    Float getAccountScore() {
        return mAccountScore;
    }

    /**
     * Get the Account Name
     *
     * @return String or {@code null}.
     */
    @Nullable
    String getAccountName() {
        return mAccountName;
    }

    @Nullable
    TrigCommand getL0Trigger() { return mL0Trigger; }

    @Nullable
    TrigCommand getQualityTrigger() {
        return mQualityTrigger;
    }

    @Nullable
    TrigCommand[] getPrecalTriggers() {
        return mPrecalTriggers;
    }

    @Nullable
    TrigCommand getL1Trigger() { return mL1Trigger; }

    @Nullable
    TrigCommand getL2Trigger() {
        return mL2Trigger;
    }

    /**
     * Get the target exposure block period.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    Integer getTargetExposureBlockPeriod() {
        return mTargetExposureBlockPeriod;
    }

    /**
     * Get the current experiment.
     *
     * @return String or {@code null}
     */
    @Nullable
    String getCurrentExperiment() {
        return (mCurrentExperiment == null || mCurrentExperiment.isEmpty()) ? null : mCurrentExperiment;
    }

    /**
     * Get the device nick name.
     *
     * @return String or {@code null}
     */
    @Nullable
    String getDeviceNickname() {
        return (mDeviceNickname == null || mDeviceNickname.isEmpty()) ? null : mDeviceNickname;
    }

    @Nullable
    String getResolution() {
        return mResolution;
    }

    @Nullable
    Float getTargetFPS() {
        return mTargetFPS;
    }

    @Nullable
    Integer getNAlloc() {
        return mNAlloc;
    }

    @Nullable
    Float getFracDeadTime() {
        return mFracDeadTime;
    }

    @Nullable
    Integer getBatteryOverheatTemp() {
        return mBatteryOverheatTemp;
    }

    @Nullable
    Boolean shouldRecalibrate() {
        return mShouldRecalibrate;
    }

    @Nullable
    Long getDataChunkSize() {
        return mDataChunkSize;
    }

}
