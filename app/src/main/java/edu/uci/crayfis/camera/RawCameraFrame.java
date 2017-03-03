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

    private static Camera mCamera;
    private static int mFrameWidth;
    private static int mFrameHeight;
    private static int mLength;

    public static final int BORDER = 0;

    // RenderScript objects

    private static RenderScript mRS;
    private static Type mType;
    private static ScriptIntrinsicHistogram mScript;
    private static Element mElement;

    /**
     * Create a new instance.
     *
     * @param bytes Raw bytes from the camera.
     * @param timestamp The time at which the image was recieved by our app.
     */
    public RawCameraFrame(@NonNull byte[] bytes, AcquisitionTime timestamp) {
        mBytes = bytes;
        mAcquiredTime = timestamp;
    }

    public static void setCamera(Camera camera, Camera.Size sz) {
        mCamera = camera;
        mFrameWidth = sz.width;
        mFrameHeight = sz.height;
        mLength = mFrameWidth * mFrameHeight;
    }

    @TargetApi(19)
    public static void useRenderScript(RenderScript rs, Type type, ScriptIntrinsicHistogram script) {
        mRS = rs;
        mType = type;
        mScript = script;
        mElement = Element.U32(rs);
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
     * @deprecated Current location could be part of the constructor, this method breaks immutability.
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

    public void setOrientation(float[] orient) {
        mOrientation = orient.clone();
    }

    public int getBatteryTemp() { return mBatteryTemp; }

    public void setBatteryTemp(int batteryTemp) {
        mBatteryTemp = batteryTemp;
    }

    public ExposureBlock getExposureBlock() { return mExposureBlock; }
    public void setExposureBlock(ExposureBlock xb) { mExposureBlock = xb; }

    private void calculateStatistics() {
        if (mPixMax >= 0) {
            // somebody beat us to it! nothing to do.
            return;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && mScript != null) {
            // create RenderScript allocation objects
            Allocation ain = Allocation.createTyped(mRS, mType, Allocation.USAGE_SCRIPT);
            Allocation aout = Allocation.createSized(mRS, mElement, 256, Allocation.USAGE_SCRIPT);
            ain.copy1DRangeFromUnchecked(0, mLength, mBytes);
            int[] hist = new int[256];

            // use built-in script to create histogram
            synchronized(mScript) {
                mScript.setOutput(aout);
                mScript.forEach(ain);
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
