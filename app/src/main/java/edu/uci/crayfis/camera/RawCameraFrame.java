package edu.uci.crayfis.camera;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.support.annotation.NonNull;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Range;

import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 *
 * TODO Jodi - Not really sure what ExposureBlock is yet.
 */
public class RawCameraFrame {

    private byte[] mBytes;
    private Mat mGreyMat;
    private final AcquisitionTime mAcquiredTime;
    private Location mLocation;
    private float[] mOrientation;
    private Camera mCamera;
    private Camera.Parameters mParams;
    private Camera.Size mSize;
    private int mLength;
    private int mPixMax;
    private double mPixAvg = -1;
    private Boolean mBufferOutstanding = true;
    private ExposureBlock mExposureBlock;
    private int mBatteryTemp;

    public static final int BORDER = 5;

    /**
     * Create a new instance.
     *
     * @param bytes Raw bytes from the camera.
     * @param timestamp The time at which the image was recieved by our app.
     * @param camera The camera instance this image came from.
     */
    public RawCameraFrame(@NonNull byte[] bytes, AcquisitionTime timestamp, int temp, Camera camera) {
        mBytes = bytes;
        Mat byteMat = new MatOfByte(bytes);
        mAcquiredTime = timestamp;
        mBatteryTemp = temp;
        mCamera = camera;
        mParams = mCamera.getParameters();
        mSize = mParams.getPreviewSize();
        mPixMax = -1;

        mLength = mSize.width * mSize.height;
        mGreyMat = byteMat
                .rowRange(0,mLength) // only use greyscale bytes
                .reshape(1, mSize.height) //
                .submat(BORDER, mSize.height-BORDER, BORDER, mSize.width-BORDER); // trim off border
    }

    public Mat getGreyMat() {
        return mGreyMat;
    }

    public byte[] getBytes() {
        return mBytes;
    }

    private byte[] createPreviewBuffer() {
        Camera.Size sz = mParams.getPreviewSize();
        int imgsize = sz.height*sz.width;
        int formatsize = ImageFormat.getBitsPerPixel(mParams.getPreviewFormat());
        int bsize = imgsize * formatsize / 8;
        CFLog.d("Creating new preview buffer; imgsize = " + imgsize + " formatsize = " + formatsize + " bsize = " + bsize);
        return new byte[bsize+1];
    }

    public void retire() {
        synchronized (mBufferOutstanding) {
            if (!mBufferOutstanding) {
                return;
            }
            mCamera.addCallbackBuffer(mBytes);
            mBufferOutstanding = false;
            mBytes = null;
            mGreyMat = null;
        }
    }

    public void claim() {
        // FIXME/TODO: add a check to ensure we have enough memory to allocate a new buffer
        synchronized (mBufferOutstanding) {
            if (!mBufferOutstanding) {
                return;
            }
            mCamera.addCallbackBuffer(createPreviewBuffer());
            mBufferOutstanding = false;
        }
    }

    // notify the XB that we are totally done processing this frame.
    public void clear() {
        synchronized (mBufferOutstanding) {
            assert mBufferOutstanding == false;
            mExposureBlock.clearFrame(this);
            // make sure we null the image buffer so that its memory can be freed.
            mBytes = null;
            mGreyMat = null;
        }
    }

    public boolean isOutstanding() {
        try {
            synchronized (mBytes) {
                return mBufferOutstanding;
            }
        } catch (NullPointerException e) {
            // mBytes is null, so we certainly don't have a buffer!
            return false;
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

    public Camera getCamera() { return mCamera; }
    public Camera.Size getSize() { return mSize; }

    public ExposureBlock getExposureBlock() { return mExposureBlock; }
    public void setExposureBlock(ExposureBlock xb) { mExposureBlock = xb; }

    private void calculateStatistics() {
        synchronized (mGreyMat) {
            if (mPixMax >= 0) {
                // somebody beat us to it! nothing to do.
                return;
            }
            Core.MinMaxLocResult result = Core.minMaxLoc(mGreyMat);
            mPixMax = (int) result.maxVal;
            mPixAvg = Core.mean(mGreyMat).val[0];
        }
    }

    public int getPixMax() {
        if (mPixMax >= 0) {
            return mPixMax;
        } else {
            calculateStatistics();
            return mPixMax;
        }
    }

    public double getPixAvg() {
        if (mPixAvg >= 0) {
            return mPixAvg;
        } else {
            calculateStatistics();
            return mPixAvg;
        }
    }
}
