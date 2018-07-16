package io.crayfis.android.exposure;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.support.annotation.CallSuper;

import org.opencv.core.Mat;

import java.util.List;
import java.util.concurrent.Semaphore;

import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.util.CFLog;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 */
public abstract class RawCameraFrame {

    byte[] mRawBytes;
    Mat mGrayMat;
    private final int[] mHist = new int[256];

    private final AcquisitionTime mAcquiredTime;
    private final Location mLocation;
    private final float[] mOrientation;
    private final float mRotationZZ;
    private final float mPressure;

    final ExposureBlock mExposureBlock;

    private int mPixMax = -1;
    private double mPixAvg = -1;
    private double mPixStd = -1;

    Boolean mBufferClaimed = false;

    DataProtos.Event.Builder mEventBuilder;
    private DataProtos.Event mEvent;
    private boolean mUploadRequested;

    // RenderScript objects

    private ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    Allocation aWeighted;
    private Allocation aout;

    // lock to make sure allocation doesn't change as we're performing weighting
    static final Semaphore weightingLock = new Semaphore(1);
    private boolean mIsWeighted = false;

    private enum FrameType {
        DEPRECATED,
        CAMERA2
    }

    /**
     * Class for creating immutable RawCameraFrames
     */
    public static class Builder {

        FrameType bFrameType;

        // Deprecated
        private byte[] bBytes;
        private Camera bCamera;

        // Camera2 YUV
        private Allocation bRaw;
        TotalCaptureResult bResult;

        AcquisitionTime bAcquisitionTime;
        long bTimestamp;
        Location bLocation;
        float[] bOrientation;
        float bRotationZZ;
        float bPressure;
        ExposureBlock bExposureBlock;

        ScriptIntrinsicHistogram bScriptIntrinsicHistogram;
        Allocation bWeighted;
        Allocation bOut;

        /**
         * Method for configuring Builder to create RawCameraDeprecatedFrames
         *
         * @param camera Camera
         * @param rs RenderScript context
         * @return Builder
         */
        public Builder setCamera(Camera camera, RenderScript rs) {

            bFrameType = FrameType.DEPRECATED;
            bCamera = camera;
            Camera.Parameters params = camera.getParameters();
            Camera.Size sz = params.getPreviewSize();

            setRenderScript(rs, sz.width, sz.height);
            return this;
        }

        public Builder setBytes(byte[] bytes) {
            bBytes = bytes;
            return this;
        }

        /**
         * Method for configuring Builder to create RawCamera2Frames
         *
         * @param  buf Allocation camera buffer
         * @param rs RenderScript context
         * @return Builder
         */
        @TargetApi(21)
        public Builder setCamera2(Allocation buf, RenderScript rs) {

            bFrameType = FrameType.CAMERA2;
            bRaw = buf;
            setRenderScript(rs, buf.getType().getX(), buf.getType().getY());

            return this;
        }

        private void setRenderScript(RenderScript rs, int width, int height) {
            Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
            Type type = tb.setX(width)
                    .setY(height)
                    .create();

            bScriptIntrinsicHistogram = ScriptIntrinsicHistogram.create(rs, Element.U8(rs));
            if(bWeighted != null) bWeighted.destroy();
            bWeighted = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT);
            bOut = Allocation.createSized(rs, Element.U32(rs), 256, Allocation.USAGE_SCRIPT);
            bScriptIntrinsicHistogram.setOutput(bOut);
        }



        public Builder setAcquisitionTime(AcquisitionTime acquisitionTime) {
            bAcquisitionTime = acquisitionTime;
            return this;
        }

        public Builder setTimestamp(long timestamp) {
            bTimestamp = timestamp;
            return this;
        }

        public Builder setCaptureResult(TotalCaptureResult result) {
            bResult = result;
            return this;
        }

        public Builder setLocation(Location location) {
            bLocation = location;
            return this;
        }

        public Builder setOrientation(float[] orientation) {
            bOrientation = orientation;
            return this;
        }

        public Builder setRotationZZ(float rotationZZ) {
            bRotationZZ = rotationZZ;
            return this;
        }

        public Builder setPressure(float pressure) {
            bPressure = pressure;
            return this;
        }

        public Builder setExposureBlock(ExposureBlock exposureBlock) {
            bExposureBlock = exposureBlock;
            return this;
        }

        public RawCameraFrame build() {
            switch (bFrameType) {
                case DEPRECATED:
                    return new RawCameraDeprecatedFrame(bBytes, bCamera,
                            bAcquisitionTime, bTimestamp, bLocation, bOrientation, bRotationZZ, bPressure,
                            bExposureBlock, bScriptIntrinsicHistogram, bWeighted, bOut);
                case CAMERA2:
                    return new RawCamera2Frame(bRaw, bAcquisitionTime,
                            bResult, bLocation, bOrientation, bRotationZZ, bPressure,
                            bExposureBlock, bScriptIntrinsicHistogram, bWeighted, bOut);
                default:
                    CFLog.e("Frame builder not configured: returning null");
                    return null;
            }
        }

    }

    RawCameraFrame(final AcquisitionTime acquisitionTime,
                   final Location location,
                   final float[] orientation,
                   final float rotationZZ,
                   final float pressure,
                   final ExposureBlock exposureBlock,
                   final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                   final Allocation in,
                   final Allocation out) {

        mAcquiredTime = acquisitionTime;
        mLocation = location;
        mOrientation = orientation;
        mRotationZZ = rotationZZ;
        mPressure = pressure;
        mExposureBlock = exposureBlock;
        mScriptIntrinsicHistogram = scriptIntrinsicHistogram;
        aWeighted = in;
        aout = out;
    }

    public final int getRawValAt(int x, int y) {
        // we don't need to worry about unweighted bytes until
        // after L1 processing
        if(!mBufferClaimed) {
            claim();
        }
        return mRawBytes[x + mExposureBlock.res_x * y] & 0xFF;
    }

    /**
     * Returns Allocation of bytes, weighted if appropriate
     *
     * @return allocation of bytes
     */
    public synchronized Allocation getWeightedAllocation() {
        if(!mIsWeighted) {
            // weighting has already been done
            mIsWeighted = true;
            weightingLock.acquireUninterruptibly();
            weightAllocation();
        }
        return aWeighted;
    }

    /**
     * Applies ScriptC_weight to bytes if applicable
     */
    protected abstract void weightAllocation();


    /**
     * Return Mat of luminance channel
     *
     * @return 2D OpenCV::Mat
     */
    public Mat getGrayMat() {
        if(!mBufferClaimed) {
            claim();
        }
        return mGrayMat;
    }

    public boolean uploadRequested() {
        return mUploadRequested;
    }

    public final void commit() {
        if(mExposureBlock != null) {
            callLocks();
            mExposureBlock.tryAssignFrame(this);
        }
    }

    void callLocks() { }

    /**
     * Return the image buffer to be used by the camera, and free all locks
     */
    @CallSuper
    public void retire() {
        if(!mBufferClaimed) {
            weightingLock.release();
            mBufferClaimed = true;
        }
    }

    /**
     * Replenish image buffer after sending frame for L2 processing
     */
    @CallSuper
    public boolean claim() {
        calculateStatistics();
        return true;
    }

    /**
     * Notify the ExposureBlock we are done with this frame, and free all memory
     */
    @CallSuper
    public void clear() {
        if(!mBufferClaimed) retire();

        mExposureBlock.clearFrame(this);
        // make sure we null the image buffer so that its memory can be freed.
        if (mGrayMat != null) {
            synchronized (mGrayMat) {
                mGrayMat.release();
                mGrayMat = null;
            }
        }
        mRawBytes = null;
    }

    private boolean isOutstanding() {
        synchronized (mBufferClaimed) {
            return !(mGrayMat == null || mBufferClaimed);
        }
    }

    /**
     * Get the epoch time with NTP corrections.
     *
     * @return long
     */
    public long getAcquiredTimeNTP() {
        return mAcquiredTime.NTP;
    }


    /** Get the epoch time.
            *
            * @return long
    */
    public long getAcquiredTime() {
        return mAcquiredTime.Sys;
    }

    /**
     * Get the precision nano time.
     *
     * @return long
     */
    public long getAcquiredTimeNano() { return mAcquiredTime.Nano; }


    /**
     * Get the location for this.
     *
     * @return {@link android.location.Location}
     */
    public Location getLocation() {
        return mLocation;
    }

    /**
     * Get the orientation of the device when the frame was captured.
     *
     * @return float[]
     */
    public float[] getOrientation() {
        return mOrientation;
    }

    public float getRotationZZ() {
        return mRotationZZ;
    }

    public float getPressure() { return mPressure; }

    public ExposureBlock getExposureBlock() { return mExposureBlock; }

    public int getCameraId() { return mExposureBlock.camera_id; }

    public int getWidth() { return mExposureBlock.res_x; }

    public int getHeight() { return mExposureBlock.res_y; }

    private void calculateStatistics() {

        if (mPixMax >= 0) {
            // somebody beat us to it! nothing to do.
            return;
        }

        int max = 255;
        int sum = 0;
        double sumDevSq = 0;

        mScriptIntrinsicHistogram.forEach(getWeightedAllocation());
        aout.copyTo(mHist);
        mExposureBlock.underflow_hist.fill(mHist);

        // find max first, so sums are easier
        while(mHist[max] == 0 && max > 0) {
            max--;
        }

        // then find average and standard deviation
        for(int i=0; i<=max; i++) {
            sum += i*mHist[i];
        }

        mPixMax = max;
        mPixAvg = (double)sum/(mExposureBlock.res_area);

        for(int i=0; i<=max; i++) {
            double dev = i - mPixAvg;
            sumDevSq += mHist[i]*dev*dev;
        }

        mPixStd = Math.sqrt(sumDevSq/(mExposureBlock.res_area-1));

    }

    public int getPixMax() {
        if (mPixMax < 0) {
            calculateStatistics();
        }
        return mPixMax;
    }

    public double getPixAvg() {
        if (mPixAvg < 0) {
            calculateStatistics();
        }
        return mPixAvg;
    }

    public double getPixStd() {
        if (mPixStd < 0) {
            calculateStatistics();
        }
        return mPixStd;
    }

    public boolean isFacingBack() {
        // if we're getting frames while the camera is off,
        // this deserves an Exception
        return mExposureBlock.camera_facing_back;
    }

    @CallSuper
    DataProtos.Event.Builder getEventBuilder() {
        if (mEventBuilder == null) {
            mEventBuilder = DataProtos.Event.newBuilder();

            mEventBuilder.setTimestamp(getAcquiredTime())
                    .setTimestampNano(getAcquiredTimeNano())
                    .setTimestampNtp(getAcquiredTimeNTP())
                    .setPressure(mPressure)
                    .setGpsLat(mLocation.getLatitude())
                    .setGpsLon(mLocation.getLongitude())
                    .setGpsFixtime(mLocation.getTime())
                    .setAvg(getPixAvg())
                    .setStd(getPixStd());
            if (mLocation.hasAccuracy()) {
                mEventBuilder.setGpsAccuracy(mLocation.getAccuracy());
            }
            if (mLocation.hasAltitude()) {
                mEventBuilder.setGpsAltitude(mLocation.getAltitude());
            }
            if(mOrientation != null) {
                mEventBuilder.setOrientX(mOrientation[0])
                        .setOrientY(mOrientation[1])
                        .setOrientZ(mOrientation[2]);
            }

            for (int val=0; val < mExposureBlock.underflow_hist.size(); val++) {
                mEventBuilder.addHist(mHist[val]);
            }

            mEventBuilder.setXbn(mExposureBlock.xbn);
        }

        return mEventBuilder;
    }

    public void setPixels(List<DataProtos.Pixel> pixels) {
        mEvent = null;
        mUploadRequested = true;
        getEventBuilder().addAllPixels(pixels);
    }

    public void setByteBlock(DataProtos.ByteBlock byteBlock) {
        mEvent = null;
        mUploadRequested = true;
        getEventBuilder().setByteBlock(byteBlock);
    }

    public void setZeroBias(DataProtos.ZeroBiasSquare zeroBiasSquare) {
        mEvent = null;
        mUploadRequested = true;
        getEventBuilder().setZeroBias(zeroBiasSquare);
    }

    public DataProtos.Event getEvent() {
        if(mEvent == null) {
            mEvent = mEventBuilder.build();
        }
        return mEvent;
    }
}
