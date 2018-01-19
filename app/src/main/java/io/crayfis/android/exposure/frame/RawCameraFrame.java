package io.crayfis.android.exposure.frame;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;

import org.opencv.core.Mat;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.util.CFLog;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 */
public abstract class RawCameraFrame {

    byte[] mRawBytes;
    Mat mGrayMat;
    private int[] mHist = new int[256];

    private final int mCameraId;
    private final boolean mFacingBack;

    final int mFrameWidth;
    final int mFrameHeight;
    final int mLength;

    private final AcquisitionTime mAcquiredTime;
    private final long mTimestamp;
    private final Location mLocation;
    private final float[] mOrientation;
    private final float mRotationZZ;
    private final float mPressure;
    private final ExposureBlock mExposureBlock;

    private int mPixMax = -1;
    private double mPixAvg = -1;
    private double mPixStd = -1;

    Boolean mBufferClaimed = false;

    private DataProtos.Event.Builder mEventBuilder;
    private DataProtos.Event mEvent;
    private boolean mUploadRequested;

    // RenderScript objects

    private ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    ScriptC_weight mScriptCWeight;
    Allocation aWeighted;
    private Allocation aout;

    // lock to make sure allocation doesn't change as we're performing weighting
    static final Semaphore weightingLock = new Semaphore(1);
    private boolean mIsWeighted = false;

    private enum FrameType {
        DEPRECATED,
        YUV
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

        int bCameraId;
        boolean bFacingBack;

        int bFrameWidth;
        int bFrameHeight;
        int bLength;

        AcquisitionTime bAcquisitionTime;
        long bTimestamp;
        Location bLocation;
        float[] bOrientation;
        float bRotationZZ;
        float bPressure;
        ExposureBlock bExposureBlock;

        ScriptIntrinsicHistogram bScriptIntrinsicHistogram;
        ScriptC_weight bScriptCWeight;
        Allocation bWeighted;
        Allocation bOut;

        /**
         * Method for configuring Builder to create RawCameraDeprecatedFrames
         *
         * @param camera Camera
         * @param cameraId int
         * @param rs RenderScript context
         * @return Builder
         */
        public Builder setCamera(Camera camera, int cameraId, RenderScript rs) {

            bFrameType = FrameType.DEPRECATED;
            bCamera = camera;
            Camera.Parameters params = camera.getParameters();
            Camera.Size sz = params.getPreviewSize();
            bFrameWidth = sz.width;
            bFrameHeight = sz.height;
            bLength = bFrameWidth * bFrameHeight;

            bCameraId = cameraId;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            bFacingBack = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK;

            setRenderScript(rs, bFrameWidth, bFrameHeight);
            return this;
        }

        public Builder setBytes(byte[] bytes) {
            bBytes = bytes;
            return this;
        }

        /**
         * Method for configuring Builder to create RawCamera2Frames
         *
         * @param manager CameraManager
         * @param cameraId int
         * @param alloc Allocation camera buffer
         * @param rs RenderScript context
         * @return Builder
         */
        @TargetApi(21)
        public Builder setCamera2(CameraManager manager, int cameraId, Allocation alloc, RenderScript rs) {

            bFrameType = FrameType.YUV;

            bRaw = alloc;

            Type type = alloc.getType();

            bFrameWidth = type.getX();
            bFrameHeight = type.getY();
            bLength = bFrameWidth * bFrameHeight;

            bCameraId = cameraId;
            try {
                String[] idList = manager.getCameraIdList();
                CameraCharacteristics cc = manager.getCameraCharacteristics(idList[cameraId]);
                Integer lensFacing = cc.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null) {
                    bFacingBack = (lensFacing == CameraMetadata.LENS_FACING_BACK);
                }
            } catch (CameraAccessException e) {
                CFLog.e("CameraAccessException");
            }

            setRenderScript(rs, bFrameWidth, bFrameHeight);

            return this;
        }

        private void setRenderScript(RenderScript rs, int width, int height) {
            Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
            Type type = tb.setX(width)
                    .setY(height)
                    .create();

            bScriptIntrinsicHistogram = ScriptIntrinsicHistogram.create(rs, Element.U8(rs));
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

        public Builder setWeights(ScriptC_weight weights) {
            bScriptCWeight = weights;
            return this;
        }

        public RawCameraFrame build() {
            switch (bFrameType) {
                case DEPRECATED:
                    return new RawCameraDeprecatedFrame(bBytes, bCamera, bCameraId, bFacingBack,
                            bFrameWidth, bFrameHeight, bLength, bAcquisitionTime, bTimestamp, bLocation,
                            bOrientation, bRotationZZ, bPressure, bExposureBlock,
                            bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
                case YUV:
                    return new RawCamera2Frame(bRaw, bCameraId, bFacingBack,
                            bFrameWidth, bFrameHeight, bLength, bAcquisitionTime, bTimestamp, bLocation,
                            bOrientation, bRotationZZ, bPressure, bExposureBlock,
                            bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
                default:
                    CFLog.e("Frame builder not configured: returning null");
                    return null;
            }
        }

    }

    RawCameraFrame(final int cameraId,
                   final boolean facingBack,
                   final int frameWidth,
                   final int frameHeight,
                   final int length,
                   final AcquisitionTime acquisitionTime,
                   final long timestamp,
                   final Location location,
                   final float[] orientation,
                   final float rotationZZ,
                   final float pressure,
                   final ExposureBlock exposureBlock,
                   final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                   final ScriptC_weight scriptCWeight,
                   final Allocation in,
                   final Allocation out) {

        mCameraId = cameraId;
        mFacingBack = facingBack;
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        mLength = length;
        mAcquiredTime = acquisitionTime;
        mTimestamp = timestamp;
        mLocation = location;
        mOrientation = orientation;
        mRotationZZ = rotationZZ;
        mPressure = pressure;
        mExposureBlock = exposureBlock;
        mScriptIntrinsicHistogram = scriptIntrinsicHistogram;
        mScriptCWeight = scriptCWeight;
        aWeighted = in;
        aout = out;
    }

    public byte getRawByteAt(int x, int y) {
        // we don't need to worry about unweighted bytes until
        // after L1 processing
        if(!mBufferClaimed) {
            claim();
        }
        return mRawBytes[x + mFrameWidth * y];
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
            weightingLock.tryAcquire();
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
        callLocks();
        mExposureBlock.tryAssignFrame(this);
    }

    void callLocks() { }

    /**
     * Return the image buffer to be used by the camera, and free all locks
     */
    public void retire() {
        weightingLock.release();
    }

    /**
     * Replenish image buffer after sending frame for L2 processing
     */
    public boolean claim() {
        // ensure that calculateStatistics has been called, so that allocated
        // data doesn't disappear.
        calculateStatistics();

        return true;
    }

    /**
     * Notify the ExposureBlock we are done with this frame, and free all memory
     */
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
     * Get the timestamp associated with the target to which the camera is rendering frames.
     *
     * @return long
     */
    public long getTimestamp() { return mTimestamp; }

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

    public int getCameraId() { return mCameraId; }

    public int getWidth() { return mFrameWidth; }

    public int getHeight() { return mFrameHeight; }

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
        mPixAvg = (double)sum/mLength;

        for(int i=0; i<=max; i++) {
            double dev = i - mPixAvg;
            sumDevSq += mHist[i]*dev*dev;
        }

        mPixStd = Math.sqrt(sumDevSq/(mLength-1));

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
        return mFacingBack;
    }

    private DataProtos.Event.Builder getEventBuilder() {
        if (mEventBuilder == null) {
            mEventBuilder = DataProtos.Event.newBuilder();

            mEventBuilder.setTimestamp(getAcquiredTime())
                    .setTimestampNano(getAcquiredTimeNano())
                    .setTimestampNtp(getAcquiredTimeNTP())
                    .setTimestampTarget(getTimestamp())
                    .setPressure(mPressure)
                    .setGpsLat(mLocation.getLatitude())
                    .setGpsLon(mLocation.getLongitude())
                    .setGpsFixtime(mLocation.getTime());
            if (mLocation.hasAccuracy()) {
                mEventBuilder.setGpsAccuracy(mLocation.getAccuracy());
            }
            if (mLocation.hasAltitude()) {
                mEventBuilder.setGpsAltitude(mLocation.getAltitude());
            }

            mEventBuilder.setOrientX(mOrientation[0])
                    .setOrientY(mOrientation[1])
                    .setOrientZ(mOrientation[2])
                    .setAvg(getPixAvg())
                    .setStd(getPixStd());
            for (int val=0; val < mExposureBlock.underflow_hist.size(); val++) {
                mEventBuilder.addHist(mHist[val]);
            }

            mEventBuilder.setXbn(mExposureBlock.getXBN());
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
