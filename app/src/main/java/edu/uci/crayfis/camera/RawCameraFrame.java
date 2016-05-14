package edu.uci.crayfis.camera;

import edu.uci.crayfis.util.CFLog;
import android.hardware.Camera;
import android.location.Location;

import edu.uci.crayfis.exposure.ExposureBlock;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 *
 * TODO Jodi - Not really sure what ExposureBlock is yet.
 */
public class RawCameraFrame {

    private final byte[] mBytes;
    private final long mAcquiredTime;
    private final long mNanoTime;
    private final long mAcquiredTimeNTP;
    private final ExposureBlock mExposureBlock;
    private Location mLocation;
    private float[] mOrientation;
    private Camera.Parameters mParams;
    private Camera.Size mSize;
    private int mPixMax;
    private int mLength;

    /**
     * Create a new instance.
     *
     * @param bytes Raw bytes from the camera.
     * @param timestamp The time in milliseconds.
     * @param exposureBlock The {@link edu.uci.crayfis.exposure.ExposureBlock}
     * @param orient The orientation of the device.
     * @param camera The camera instance this image came from.
     */
    public RawCameraFrame(byte[] bytes, long timestamp, long nanoTime, long timestamp_ntp,ExposureBlock exposureBlock, float[] orient, Camera camera) {
        mBytes = bytes;
        mAcquiredTime = timestamp;
        mNanoTime = nanoTime;
        mAcquiredTimeNTP = timestamp_ntp;
        mExposureBlock = exposureBlock;
        mOrientation = orient.clone();
        mParams = camera.getParameters();
        mSize = mParams.getPreviewSize();
        mPixMax = -1;

        mLength = mSize.height * mSize.width;
    }

    /**
     * Get the raw bytes for this frame.
     *
     * @return byte[]
     */
    public byte[] getBytes() {
        return mBytes;
    }

    /**
     * Get the epoch time with NTP corrections.
     *
     * @return long
     */
    public long getAcquiredTimeNTP() {
        return mAcquiredTimeNTP;
    }


    /** Get the epoch time.
            *
            * @return long
    */
    public long getAcquiredTime() {
        return mAcquiredTime;
    }

    /**
     * Get the precision nano time.
     *
     * @return long
     */
    public long getNanoTime() { return mNanoTime; }

    /**
     * Get the {@link edu.uci.crayfis.exposure.ExposureBlock}
     *
     * @return {@link edu.uci.crayfis.exposure.ExposureBlock}
     */
    public ExposureBlock getExposureBlock() {
        return mExposureBlock;
    }

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

    public Camera.Parameters getParams() { return mParams; }
    public Camera.Size getSize() { return mSize; }

    public int getPixMax() {
        if (mPixMax >= 0) {
            return mPixMax;
        }

        for (int i = 0; i < mLength; i++) {
            mPixMax = Math.max(mPixMax, mBytes[i]&0xFF);
            //int val = mBytes[i] & 0xFF;
            //if (val > mPixMax) mPixMax = val;
        }

        return mPixMax;
    }
}
