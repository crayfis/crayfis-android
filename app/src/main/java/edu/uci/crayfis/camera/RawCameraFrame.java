package edu.uci.crayfis.camera;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.location.Location;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.support.annotation.NonNull;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import java.util.Arrays;

import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 */
public class RawCameraFrame {

    private byte[] mBytes;
    private Mat mGrayMat;
    private final AcquisitionTime mAcquiredTime;
    private Location mLocation;
    private float[] mOrientation;
    private int mBatteryTemp;
    private int mPixMax = -1;
    private double mPixAvg = -1;
    private double mPixStd = -1;
    private Boolean mBufferClaimed = false;
    private ExposureBlock mExposureBlock;

    private Camera mCamera;
    private int mCameraId;
    private int mFrameWidth;
    private int mFrameHeight;
    private int mLength;

    public static final int BORDER = 10;

    // RenderScript objects

    private ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    private ScriptC_weight mScriptCWeight;
    private Allocation ain;
    private Allocation aweights;
    private Allocation aout;

    /**
     * Class for creating immutable RawCameraFrames
     */
    public static class Builder {
        private byte[] bBytes;
        private Camera bCamera;
        private int bCameraId;
        private int bFrameWidth;
        private int bFrameHeight;
        private int bLength;
        private AcquisitionTime bAcquisitionTime;
        private Location bLocation;
        private float[] bOrientation;
        private int bBatteryTemp;
        private ExposureBlock bExposureBlock;
        private ScriptIntrinsicHistogram bScriptIntrinsicHistogram;
        private ScriptC_weight bScriptCWeight;
        private Allocation bin;
        private Allocation bweights;
        private Allocation bout;

        public Builder() {

        }

        public Builder setBytes(byte[] bytes) {
            bBytes = bytes;
            return this;
        }

        public Builder setCamera(Camera camera) {
            bCamera = camera;
            Camera.Parameters params = camera.getParameters();
            Camera.Size sz = params.getPreviewSize();
            bFrameWidth = sz.width;
            bFrameHeight = sz.height;
            bLength = bFrameWidth * bFrameHeight;
            return this;
        }

        @TargetApi(19)
        public Builder setCamera(Camera camera, RenderScript rs) {

            setCamera(camera);

            Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
            Type type = tb.setX(bFrameWidth)
                    .setY(bFrameHeight)
                    .create();
            bScriptIntrinsicHistogram = ScriptIntrinsicHistogram.create(rs, Element.U8(rs));
            bin = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT);
            bweights = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT);
            bout = Allocation.createSized(rs, Element.U32(rs), 256, Allocation.USAGE_SCRIPT);
            bScriptIntrinsicHistogram.setOutput(bout);

            // define aweights
            byte[] maskArray = new byte[(bFrameWidth-2*BORDER)*(bFrameHeight-2*BORDER)];

            // for now, just use equal weights
            byte b1 = (byte)1;
            Arrays.fill(maskArray, b1);

            bweights.copy2DRangeFrom(BORDER, BORDER, bFrameWidth-2*BORDER, bFrameHeight-2*BORDER, maskArray);
            bScriptCWeight = new ScriptC_weight(rs);
            bScriptCWeight.set_weights(bweights);

            return this;
        }

        public Builder setCameraId(int cameraId) {
            bCameraId = cameraId;
            return this;
        }

        public Builder setAcquisitionTime(AcquisitionTime acquisitionTime) {
            bAcquisitionTime = acquisitionTime;
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

        public Builder setBatteryTemp(int batteryTemp) {
            bBatteryTemp = batteryTemp;
            return this;
        }

        public Builder setExposureBlock(ExposureBlock exposureBlock) {
            bExposureBlock = exposureBlock;
            return this;
        }

        public RawCameraFrame build() {
            return new RawCameraFrame(bBytes, bCamera, bCameraId, bFrameWidth, bFrameHeight,
                    bLength, bAcquisitionTime, bLocation, bOrientation, bBatteryTemp, bExposureBlock,
                    bScriptIntrinsicHistogram, bScriptCWeight, bin, bweights, bout);
        }
    }

    /**
     * Create a new instance via Builder class
     *
     * @param bytes
     * @param camera
     * @param cameraId
     * @param frameWidth
     * @param frameHeight
     * @param length
     * @param acquisitionTime
     * @param location
     * @param orientation
     * @param batteryTemp
     * @param exposureBlock
     * @param scriptIntrinsicHistogram
     * @param scriptCWeight
     * @param in
     * @param weights
     * @param out
     */
    private RawCameraFrame(@NonNull final byte[] bytes,
                           final Camera camera,
                           final int cameraId,
                           final int frameWidth,
                           final int frameHeight,
                           final int length,
                           final AcquisitionTime acquisitionTime,
                           final Location location,
                           final float[] orientation,
                           final int batteryTemp,
                           final ExposureBlock exposureBlock,
                           final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                           final ScriptC_weight scriptCWeight,
                           final Allocation in,
                           final Allocation weights,
                           final Allocation out) {
        mBytes = bytes;
        mCamera = camera;
        mCameraId = cameraId;
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        mLength = length;
        mAcquiredTime = acquisitionTime;
        mLocation = location;
        mOrientation = orientation;
        mBatteryTemp = batteryTemp;
        mExposureBlock = exposureBlock;
        mScriptIntrinsicHistogram = scriptIntrinsicHistogram;
        mScriptCWeight = scriptCWeight;
        ain = in;
        aweights = weights;
        aout = out;
    }


    /**
     * Return Mat from image buffer, create if necessary
     *
     * @return 2D OpenCV::Mat
     */
    public Mat getGrayMat() {
        if (mGrayMat == null) {

            synchronized (mBytes) {

                // probably a better way to do this, but this
                // works for preventing native memory leaks

                Mat mat1 = new MatOfByte(mBytes);
                Mat mat2 = mat1.rowRange(0, mLength); // only use grayscale byte
                mat1.release();
                Mat mat3 = mat2.reshape(1, mFrameHeight); // create 2D array
                mat2.release();
                mGrayMat = mat3.submat(BORDER, mFrameHeight - BORDER, BORDER, mFrameWidth - BORDER); // trim off border
                mat3.release();

                // don't need bytes anymore
                replenishBuffer();
            }
        }
        return mGrayMat;
    }

    /**
     * Free memory from mBytes and add as PreviewCallback buffer
     */
    private void replenishBuffer() {
        synchronized (mCamera) {
            if (mBytes != null) {
                mCamera.addCallbackBuffer(mBytes);
                mBytes = null;
            }
        }
    }

    /**
     * Free memory from Mat
     */
    private void releaseMat() {

        if (mGrayMat != null) {
            synchronized (mGrayMat) {
                mGrayMat.release();
                mGrayMat = null;
            }
        }
    }

    /**
     * Clear memory from RawCameraFrame
     */
    public void retire() {
        replenishBuffer();
        releaseMat();
    }

    /**
     * Replenish image buffer after sending frame for L2 processing
     */
    public void claim() {
        if(mGrayMat == null) {
            getGrayMat();
        }
        mBufferClaimed = true;
    }

    // notify the XB that we are totally done processing this frame.
    public void clear() {
        mExposureBlock.clearFrame(this);
        // make sure we null the image buffer so that its memory can be freed.
        releaseMat();
    }

    public boolean isOutstanding() {
        synchronized (mBufferClaimed) {
            return !(mBytes == null && (mGrayMat == null || mBufferClaimed));
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
     * Set the {@link android.location.Location}.
     *
     * @param location {@link android.location.Location}
     */
    public void setLocation(final Location location) {
        mLocation = location;
    }

    /**
     * Get the orientation of the device when the frame was captured.
     *
     * @return float[]
     */
    public float[] getOrientation() {
        return mOrientation;
    }

    public int getBatteryTemp() { return mBatteryTemp; }

    public ExposureBlock getExposureBlock() { return mExposureBlock; }

    public int getCameraId() { return mCameraId; }

    private void calculateStatistics() {
        if (mPixMax >= 0) {
            // somebody beat us to it! nothing to do.
            return;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && mScriptIntrinsicHistogram != null) {

            int[] hist = new int[256];

            synchronized (mScriptIntrinsicHistogram) {
                ain.copy1DRangeFromUnchecked(0, mLength, mBytes);
                mScriptCWeight.forEach_weight(ain, ain);
                mScriptIntrinsicHistogram.forEach(ain);
                aout.copyTo(hist);
            }

            int max = 0;
            int sum = 0;
            for(int i=0; i<256; i++) {
                sum += i*hist[i];
                if(hist[i] != 0) {
                    max = i;
                }
            }
            mPixMax = max;
            mPixAvg = (double)sum/mLength;

        } else {
            getGrayMat();
            synchronized (mGrayMat) {
                mPixMax = (int) Core.minMaxLoc(mGrayMat).maxVal;
                mPixAvg = Core.mean(mGrayMat).val[0];
                mPixStd = 0; // don't think we actually care about this enough to justify the CPU use
            }
        }
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
}
