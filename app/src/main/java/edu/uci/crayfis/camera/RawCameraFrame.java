package edu.uci.crayfis.camera;

import android.location.Location;

import edu.uci.crayfis.exposure.ExposureBlock;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 *
 * TODO Jodi - Not really sure what ExposureBlock is yet.
 * TODO Jodi - Is epoch in seconds or milliseconds?
 */
public class RawCameraFrame {

    private final byte[] mBytes;
    private final long mAcquiredTime;
    private final ExposureBlock mExposureBlock;
    private Location mLocation;
    private float[] mOrientation;

    /**
     * Create a new instance.
     *
     * @param bytes Raw bytes from the camera.
     * @param t The time.
     * @param exposureBlock The {@link edu.uci.crayfis.exposure.ExposureBlock}
     * @param orient The orientation of the device.
     */
    public RawCameraFrame(byte[] bytes, long t, ExposureBlock exposureBlock, float[] orient) {
        mBytes = bytes;
        mAcquiredTime = t;
        mExposureBlock = exposureBlock;
        mOrientation = orient.clone();
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
     * Get the epoch time.
     *
     * @return long
     */
    public long getAcquiredTime() {
        return mAcquiredTime;
    }

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
}
