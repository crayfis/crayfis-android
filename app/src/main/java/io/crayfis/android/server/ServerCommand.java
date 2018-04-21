package io.crayfis.android.server;

import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wrapper class for parsing a server command response.  This uses objects rather than primitives
 * because I'm not sure what the valid value ranges are, 0 may be valid, same with negative numbers.
 * Getters return {@code null} when their values have not been set.
 *
 * TODO This is based on the assumption that the JSON response only has fields that should change.  Is this really true?
 */
class ServerCommand {

    @SerializedName("set_precal") private String[] mPrecalWeights;
    @SerializedName("set_hotcells") private List<Set<String>> mHotcells;
    @SerializedName("set_precal_reset_time") private Long mPrecalResetTime;
    @SerializedName("set_L0_trig") private String mL0Trigger;
    @SerializedName("set_qual_trig") private String mQualityTrigger;
    @SerializedName("set_precal_trig") private String mPrecalTrigger;
    @SerializedName("set_L1_trig") private String mL1Trigger;
    @SerializedName("set_L2_trig") private String mL2Trigger;
    @SerializedName("set_xb_period") private Integer mTargetExposureBlockPeriod;
    @SerializedName("cmd_recalibrate") private Boolean mShouldRecalibrate;
    @SerializedName("experiment") private String mCurrentExperiment;
    @SerializedName("nickname") private String mDeviceNickname;
    @SerializedName("account_name") private String mAccountName;
    @SerializedName("account_score") private Float mAccountScore;
    @SerializedName("set_target_resolution") private String mResolution;
    @SerializedName("set_target_fps") private Float mTargetFPS;
    @SerializedName("set_frac_dead_time") private Float mFracDeadTime;
    @SerializedName("set_battery_overheat_temp") private Integer mBatteryOverheatTemp;


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
    String[] getPrecalWeights() {
        return mPrecalWeights;
    }

    @Nullable
    List<Set<String>> getHotcells() {
        return mHotcells;
    }

    @Nullable
    Long getPrecalResetTime() {
        return mPrecalResetTime;
    }

    @Nullable
    String getL0Trigger() { return mL0Trigger; }

    @Nullable
    String getQualityTrigger() {
        return mQualityTrigger;
    }

    @Nullable
    String getPrecalTrigger() {
        return mPrecalTrigger;
    }

    @Nullable
    String getL1Trigger() { return mL1Trigger; }

    @Nullable
    String getL2Trigger() { return mL2Trigger; }

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

}
