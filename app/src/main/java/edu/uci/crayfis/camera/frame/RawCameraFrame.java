package edu.uci.crayfis.camera.frame;

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
import android.support.annotation.NonNull;
import android.util.Size;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import java.util.concurrent.locks.ReentrantLock;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.AcquisitionTime;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

import static android.content.Context.CAMERA_SERVICE;
import static edu.uci.crayfis.CFApplication.MODE_AUTO_DETECT;
import static edu.uci.crayfis.CFApplication.MODE_BACK_LOCK;
import static edu.uci.crayfis.CFApplication.MODE_FACE_DOWN;
import static edu.uci.crayfis.CFApplication.MODE_FRONT_LOCK;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 */
public abstract class RawCameraFrame {

    byte[] mRawBytes;

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
    private final int mBatteryTemp;
    private final ExposureBlock mExposureBlock;

    private int mPixMax = -1;
    private double mPixAvg = -1;
    private double mPixStd = -1;

    private Boolean mBufferClaimed = false;

    // RenderScript objects

    private ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    ScriptC_weight mScriptCWeight;
    Allocation aWeighted;
    private Allocation aout;

    static final ReentrantLock lock = new ReentrantLock();

    Mat mGrayMat;

    /**
     * Class for creating immutable RawCameraFrames
     */
    public static class Builder {
        private byte[] bBytes;
        private Camera bCamera;

        private Allocation bAlloc;

        private int bCameraId;
        private boolean bFacingBack;
        private int bFrameWidth;
        private int bFrameHeight;
        private int bLength;
        private AcquisitionTime bAcquisitionTime;
        private long bTimestamp;
        private Location bLocation;
        private float[] bOrientation;
        private float bRotationZZ;
        private float bPressure;
        private int bBatteryTemp;
        private ExposureBlock bExposureBlock;

        private ScriptIntrinsicHistogram bScriptIntrinsicHistogram;
        private ScriptC_weight bScriptCWeight;
        private Allocation bWeighted;
        private Allocation bOut;

        private Boolean bDeprecated;

        public Builder() {

        }

        public Builder setBytes(byte[] bytes) {
            bBytes = bytes;
            return this;
        }

        public Builder setAlloc(Allocation alloc) {
            bAlloc = alloc;
            return this;
        }

        public Builder setCamera(Camera camera, int cameraId, RenderScript rs) {

            bDeprecated = true;

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

        @TargetApi(21)
        public Builder setCamera2(CameraManager manager, int cameraId, Size sz, RenderScript rs) {

            bDeprecated = false;

            bFrameWidth = sz.getWidth();
            bFrameHeight = sz.getHeight();
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

        public RawCameraFrame build() {

            if(bDeprecated == null) {
                CFLog.e("Camera has not been set");
                return null;
            }
            if(bDeprecated) {
                return new RawCameraDeprecatedFrame(bBytes, bCamera, bCameraId, bFacingBack,
                        bFrameWidth, bFrameHeight, bLength, bAcquisitionTime, bTimestamp, bLocation,
                        bOrientation, bRotationZZ, bPressure, bBatteryTemp, bExposureBlock,
                        bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
            }
            else {
                return new RawCamera2Frame(bAlloc, bCameraId, bFacingBack,
                        bFrameWidth, bFrameHeight, bLength, bAcquisitionTime, bTimestamp, bLocation,
                        bOrientation, bRotationZZ, bPressure, bBatteryTemp, bExposureBlock,
                        bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
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
        return mRawBytes[x + mFrameWidth * y];
    }

    /**
     * Copies byte array into an allocation
     *
     * @return allocation of bytes
     */
    public Allocation getWeightedAllocation() {
        return aWeighted;
    }


    /**
     * Return Mat from image buffer, create if necessary, and recycle buffer to camera
     *
     * @return 2D OpenCV::Mat
     */
    public Mat getGrayMat() {
        return mGrayMat;
    }

    /**
     * Clear memory from RawCameraFrame
     */
    public void retire() {
        if(lock.isHeldByCurrentThread()) {
            CFLog.d("Unlocked (retire) by " + Thread.currentThread().getName());
            lock.unlock();
        }
    }

    /**
     * Replenish image buffer after sending frame for L2 processing
     */
    public void claim() {
        mBufferClaimed = true;
        getGrayMat();
    }

    // notify the XB that we are totally done processing this frame.
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
        int max = 0;
        int sum = 0;

        lock.lock();
        getWeightedAllocation();
        mScriptIntrinsicHistogram.forEach(aWeighted);
        aout.copyTo(hist);

        for(int i=0; i<256; i++) {
            sum += i*hist[i];
            if(hist[i] != 0) {
                max = i;
            }
        }

        mPixMax = max;
        mPixAvg = (double)sum/mLength;

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
        //if (mPixStd < 0) {
        //    calculateStatistics();
        //}
        return mPixStd;
    }

    public boolean isQuality() {
        final CFConfig CONFIG = CFConfig.getInstance();
        switch(CONFIG.getCameraSelectMode()) {
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
                    if(CFConfig.getInstance().getCameraSelectMode() == MODE_FACE_DOWN) {
                        CFApplication.badFlatEvents++;
                    }
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
