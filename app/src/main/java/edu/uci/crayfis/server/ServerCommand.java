package edu.uci.crayfis.server;

import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Wrapper class for parsing a server command response.  This uses objects rather than primitives
 * because I'm not sure what the valid value ranges are, 0 may be valid, same with negative numbers.
 * Getters return {@code null} when their values have not been set.
 *
 * TODO This is based on the assumption that the JSON response only has fields that should change.  Is this really true?
 */
public class ServerCommand {

    @SerializedName("set_L1_thresh") private Integer mL1Threshold;
    @SerializedName("set_L2_thresh") private Integer mL2Threshold;
    @SerializedName("set_target_L2_rate") private Float mEventsPerMinute;
    @SerializedName("calibration_sample_frames") private Integer mCalibrationSampleFrames;
    @SerializedName("set_xb_period") private Integer mTargetExposureBlockPeriod;
    @SerializedName("set_max_upload_interval") private Integer mMaxUploadInterval; // TODO This does not exist in CFConfig
    @SerializedName("set_upload_size_max") private Integer mMaxChunkSize; // TODO This does not exist in CFConfig
    @SerializedName("set_cache_upload_interval") private Integer mMinCacheUploadInterval; // TODO This does not exist in CFConfig
    @SerializedName("set_qual_pix_frac") private Float mQualPixFrac; // TODO This is all commented out in ParticleReco
    @SerializedName("set_qual_bg_avg") private Float mQualityBgAverage;
    @SerializedName("set_qual_bg_var") private Float mQualityBgVariance;
    @SerializedName("cmd_recalibrate") private Boolean mShouldRecalibrate;

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

    /**
     * Get the events per minute.
     *
     * @return Float or {@code null}.
     */
    @Nullable
    public Float getEventsPerMinute() {
        return mEventsPerMinute;
    }

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
    public Float getQualPixFrac() {
        return mQualPixFrac;
    }

    /**
     * TODO What is this?
     *
     * @return Float or {@code null} if not set.
     */
    @Nullable
    public Float getQualityBgAverage() {
        return mQualityBgAverage;
    }

    /**
     * TODO What is this?
     *
     * @return Float or {@code null} if not set.
     */
    @Nullable
    public Float getQualityBgVariance() {
        return mQualityBgVariance;
    }

    /**
     * Check if the {@link edu.uci.crayfis.DAQActivity.ExposureBlockManager} should be restarted or not.
     *
     * @return {@code true} if it should be restarted, {@code false} if not.
     */
    public boolean shouldRestartEBManager() {
        return mL1Threshold != null ||
                mL2Threshold != null ||
                mQualPixFrac != null ||
                mQualityBgAverage != null ||
                mQualityBgVariance != null;
    }

    /**
     * Check if the app should recalibrate or not.
     *
     * @return {@code true} if it should, {@code false} if not.
     */
    public boolean shouldRecalibrate() {
        return (mShouldRecalibrate == null) ? false : mShouldRecalibrate;
    }
}
