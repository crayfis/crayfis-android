package edu.uci.crayfis.server;

import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.HashSet;

/**
 * Wrapper class for parsing a server command response.  This uses objects rather than primitives
 * because I'm not sure what the valid value ranges are, 0 may be valid, same with negative numbers.
 * Getters return {@code null} when their values have not been set.
 *
 * TODO This is based on the assumption that the JSON response only has fields that should change.  Is this really true?
 */
public class ServerCommand {

    @SerializedName("set_precal") private String[] mPrecal;
    @SerializedName("set_precal_id") private long[] mPrecalId;
    @SerializedName("set_L1_thresh") private Integer mL1Threshold;
    @SerializedName("set_L2_thresh") private Integer mL2Threshold;
    @SerializedName("set_L1_trig") private String mL1Trigger;
    @SerializedName("set_L2_trig") private String mL2Trigger;
    @SerializedName("set_trigger_lock") private Boolean mTriggerLock;
    @SerializedName("set_target_L2_rate") private Float mEventsPerMinute;
    @SerializedName("weighting_sample_frames") private Integer mWeightingSampleFrames;
    @SerializedName("calibration_sample_frames") private Integer mCalibrationSampleFrames;
    @SerializedName("set_xb_period") private Integer mTargetExposureBlockPeriod;
    @SerializedName("set_max_upload_interval") private Integer mMaxUploadInterval;
    @SerializedName("set_upload_size_max") private Integer mMaxChunkSize;
    @SerializedName("set_cache_upload_interval") private Integer mMinCacheUploadInterval;
    @SerializedName("set_qual_pix_frac") private Float mQualityPixFrac;
    @SerializedName("set_qual_bg_avg") private Float mQualityBgAverage;
    @SerializedName("set_qual_bg_var") private Float mQualityBgVariance;
    @SerializedName("set_qual_orientation") private Float mQualityOrient;
    @SerializedName("cmd_recalibrate") private Boolean mShouldRecalibrate;
    @SerializedName("experiment") private String mCurrentExperiment;
    @SerializedName("nickname") private String mDeviceNickname;
    @SerializedName("account_name") private String mAccountName;
    @SerializedName("account_score") private Float mAccountScore;
    @SerializedName("update_url") private String mUpdateURL;
    @SerializedName("set_target_resolution") private String mResolution;
    @SerializedName("set_camera_select_mode") private String mCameraSelectModeString;


    /**
     * Get the Account Name
     *
     * @return String or {@code null}.
     */
    @Nullable
    public String getUpdateURL() {
        return mUpdateURL;
    }

    /**
     * Get the Account Score
     *
     * @return Long or {@code null}.
     */
    @Nullable
    public Float getAccountScore() {
        return mAccountScore;
    }

    /**
     * Get the Account Name
     *
     * @return String or {@code null}.
     */
    @Nullable
    public String getAccountName() {
        return mAccountName;
    }

    @Nullable
    public String[] getPrecal() { return mPrecal; }

    @Nullable
    public long[] getPrecalId() { return mPrecalId; }

    /**
     * Get the L1 threshold.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    public Integer getL1Threshold() {
        return mL1Threshold;
    }

    /**
     * Get the L2 threshold.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    public Integer getL2Threshold() {
        return mL2Threshold;
    }

    @Nullable
    public String getL1Trigger() { return mL1Trigger; };

    @Nullable
    public String getL2Trigger() { return mL2Trigger; };

    @Nullable
    public Boolean getTriggerLock() { return mTriggerLock; };

    /**
     * Get the events per minute.
     *
     * @return Float or {@code null}.
     */
    @Nullable
    public Float getEventsPerMinute() {
        return mEventsPerMinute;
    }

    @Nullable
    public Integer getWeightingSampleFrames() { return mWeightingSampleFrames; }

    /**
     * Get the calibration sample frames.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    public Integer getCalibrationSampleFrames() {
        return mCalibrationSampleFrames;
    }

    /**
     * Get the target exposure block period.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    public Integer getTargetExposureBlockPeriod() {
        return mTargetExposureBlockPeriod;
    }

    /**
     * Get the max upload interval.
     *
     * @return
     */
    @Nullable
    public Integer getMaxUploadInterval() {
        return mMaxUploadInterval;
    }

    /**
     * Get the max chunk size.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    public Integer getMaxChunkSize() {
        return mMaxChunkSize;
    }

    /**
     * Get the minimum cache upload interval.
     *
     * @return Integer or {@code null}.
     */
    @Nullable
    public Integer getMinCacheUploadInterval() {
        return mMinCacheUploadInterval;
    }

    /**
     * TODO What is this?
     *
     * @return Float or {@code null}.
     */
    @Nullable
    public Float getQualityPixFrac() {
        return mQualityPixFrac;
    }

    /**
     * Get cutoff for average pixel value in frame before flagged as "bad"
     *
     * @return Float or {@code null} if not set.
     */
    @Nullable
    public Float getQualityBgAverage() {
        return mQualityBgAverage;
    }

    /**
     * Get cutoff for pixel variance in frame before flagged as "bad"
     *
     * @return Float or {@code null} if not set.
     */
    @Nullable
    public Float getQualityBgVariance() {
        return mQualityBgVariance;
    }

    /**
     * Get cutoff for orientation (in degrees) about x and y axes before frame
     * is flagged as "bad"
     *
     * @return Float or {@code null} if not set
     */
    @Nullable
    public Float getQualityOrientation() { return mQualityOrient; }

    /**
     * Check if the app should recalibrate or not.
     *
     * @return {@code true} if it should, {@code false} if not.
     */
    public boolean shouldRecalibrate() {
        return (mShouldRecalibrate == null) ? false : mShouldRecalibrate;
    }

    /**
     * Get the current experiment.
     *
     * @return String or {@code null}
     */
    @Nullable
    public String getCurrentExperiment() {
        return (mCurrentExperiment == null || mCurrentExperiment.isEmpty()) ? null : mCurrentExperiment;
    }

    /**
     * Get the device nick name.
     *
     * @return String or {@code null}
     */
    @Nullable
    public String getDeviceNickname() {
        return (mDeviceNickname == null || mDeviceNickname.isEmpty()) ? null : mDeviceNickname;
    }

    @Nullable
    public String getResolution() {
        return mResolution;
    }

    @Nullable
    public Integer getCameraSelectMode() {
        try {
            return Integer.parseInt(mCameraSelectModeString);
        } catch(NumberFormatException e) {
            return null;
        }
    }
}
