package io.crayfis.android.camera;

import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import java.util.concurrent.locks.ReentrantLock;

import io.crayfis.android.server.CFConfig;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.util.CFLog;

import static io.crayfis.android.CFApplication.MODE_AUTO_DETECT;
import static io.crayfis.android.CFApplication.MODE_BACK_LOCK;
import static io.crayfis.android.CFApplication.MODE_FACE_DOWN;
import static io.crayfis.android.CFApplication.MODE_FRONT_LOCK;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 */
public abstract class RawCameraFrame {

    byte[] mRawBytes;

    private final int mCameraId;
    private final boolean mFacingBack;

    private final int mFrameWidth;
    private final int mFrameHeight;
    private final int mLength;
    private final int mBufferSize;

    private final AcquisitionTime mAcquiredTime;
    private final long mTimestamp;
    private final Location mLocation;
    private final float[] mOrientation;
    private final float mRotationZZ;
    private final float mPressure;
    private final int mBatteryTemp;
    private final ExposureBlock mExposureBlock;

    private int mPixMax = -1;
    private double mPixAvg = -1;
    private double mPixStd = -1;

    Boolean mBufferClaimed = false;

    // RenderScript objects

    private ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    ScriptC_weight mScriptCWeight;
    Allocation aWeighted;
    private Allocation aout;

    // lock to make sure allocation doesn't change as we're performing weighting
    private static final ReentrantLock weightingLock = new ReentrantLock();

    private Mat mGrayMat;

    /**
     * Class for creating immutable RawCameraFrames
     */
    public static abstract class Builder {

        int bCameraId;
        boolean bFacingBack;

        int bFrameWidth;
        int bFrameHeight;
        int bLength;
        int bBufferSize;

        AcquisitionTime bAcquisitionTime;
        long bTimestamp;
        Location bLocation;
        float[] bOrientation;
        float bRotationZZ;
        float bPressure;
        int bBatteryTemp;
        ExposureBlock bExposureBlock;

        ScriptIntrinsicHistogram bScriptIntrinsicHistogram;
        ScriptC_weight bScriptCWeight;
        Allocation bWeighted;Allocation bOut;

        void setRenderScript(RenderScript rs, int width, int height) {
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

        public Builder setBatteryTemp(int batteryTemp) {
            bBatteryTemp = batteryTemp;
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

    }

    RawCameraFrame(final int cameraId,
                   final boolean facingBack,
                   final int frameWidth,
                   final int frameHeight,
                   final int length,
                   final int bufferSize,
                   final AcquisitionTime acquisitionTime,
                   final long timestamp,
                   final Location location,
                   final float[] orientation,
                   final float rotationZZ,
                   final float pressure,
                   final int batteryTemp,
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
        mBufferSize = bufferSize;
        mAcquiredTime = acquisitionTime;
        mTimestamp = timestamp;
        mLocation = location;
        mOrientation = orientation;
        mRotationZZ = rotationZZ;
        mPressure = pressure;
        mBatteryTemp = batteryTemp;
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
        if(!weightingLock.isHeldByCurrentThread()) {
            // weighting has already been done
            weightingLock.lock();
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

    /**
     * Create Mat from Allocation and recycle buffer to camera
     *
     * @return byte[]
     */
    protected byte[] createMatAndReturnBuffer() {

        //FIXME: this is way too much copying
        byte[] adjustedBytes = new byte[mBufferSize];

        // update with weighted pixels
        aWeighted.copyTo(adjustedBytes);

        weightingLock.unlock();


        // probably a better way to do this, but this
        // works for preventing native memory leaks

        Mat mat1 = new MatOfByte(adjustedBytes);
        Mat mat2 = mat1.rowRange(0, mLength); // only use grayscale byte
        mat1.release();
        mGrayMat = mat2.reshape(1, mFrameHeight); // create 2D array
        mat2.release();

        return adjustedBytes;
    }

    /**
     * Return the image buffer to be used by the camera, and free all locks
     */
    public void retire() {
        if(weightingLock.isHeldByCurrentThread()) {
            weightingLock.unlock();
        }
    }

    /**
     * Replenish image buffer after sending frame for L2 processing
     */
    public void claim() {
        mBufferClaimed = true;
        createMatAndReturnBuffer();
    }

    /**
     * Notify the ExposureBlock we are done with this frame, and free all memory
     */
    public void clear() {
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

    public boolean isOutstanding() {
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

    public float getPressure() { return mPressure; }

    public int getBatteryTemp() { return mBatteryTemp; }

    public ExposureBlock getExposureBlock() { return mExposureBlock; }

    public int getCameraId() { return mCameraId; }

    public int getWidth() { return mFrameWidth; }

    public int getHeight() { return mFrameHeight; }

    private void calculateStatistics() {

        if (mPixMax >= 0) {
            // somebody beat us to it! nothing to do.
            return;
        }
        int[] hist = new int[256];
        int max = 255;
        int sum = 0;
        double sumDevSq = 0;

        mScriptIntrinsicHistogram.forEach(getWeightedAllocation());
        aout.copyTo(hist);

        // find max first, so sums are easier
        while(hist[max] == 0 && max > 0) {
            max--;
        }

        // then find average and standard deviation
        for(int i=0; i<=max; i++) {
            sum += i*hist[i];
        }

        mPixMax = max;
        mPixAvg = (double)sum/mLength;

        for(int i=0; i<=max; i++) {
            double dev = i - mPixAvg;
            sumDevSq += hist[i]*dev*dev;
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

    /**
     * @return false if one or more criteria for quality is not met, based on CameraSelectMode
     */
    public boolean isQuality() {
        final CFConfig CONFIG = CFConfig.getInstance();
        final int cameraSelectMode = CONFIG.getCameraSelectMode();
        switch(cameraSelectMode) {
            case MODE_FACE_DOWN:
                if (mOrientation == null) {
                    CFLog.e("Orientation not found");
                } else {

                    // use quaternion algebra to calculate cosine of angle between vertical
                    // and phone's z axis (up to a sign that tends to have numerical instabilities)

                    if(Math.abs(mRotationZZ) < CONFIG.getQualityOrientationCosine()
                            || mFacingBack != mRotationZZ>0) {

                        CFLog.w("Bad event: Orientation = " + mRotationZZ);
                        return false;
                    }
                }
            case MODE_AUTO_DETECT:
                if (getPixAvg() > CONFIG.getQualityBgAverage()
                        || getPixStd() > CONFIG.getQualityBgVariance()) {
                    CFLog.w("Bad event: Pix avg = " + mPixAvg + ">" + CONFIG.getQualityBgAverage());
                    return false;
                } else {
                    return true;
                }
            case MODE_BACK_LOCK:
                return mFacingBack;
            case MODE_FRONT_LOCK:
                return !mFacingBack;
            default:
                throw new RuntimeException("Invalid camera select mode");
        }
    }

    /**
     * Interface for listening for frames
     */
    public interface Callback {

        void onRawCameraFrame(RawCameraFrame frame);
    }
}
