package io.crayfis.android.exposure;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.AcquisitionTime;

/**
 * Created by Jeff on 9/2/2017.
 */

class RawCameraDeprecatedFrame extends RawCameraFrame {

    private final Camera mCamera;
    private final long mTimestamp;

    RawCameraDeprecatedFrame(@NonNull final byte[] bytes,
                             final Camera camera,
                             final AcquisitionTime acquisitionTime,
                             final long timestamp,
                             final Location location,
                             final float[] orientation,
                             final float rotationZZ,
                             final float pressure,
                             final ExposureBlock exposureBlock,
                             final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                             final Allocation in,
                             final Allocation out) {

        super(acquisitionTime, location, orientation, rotationZZ, pressure,
                exposureBlock, scriptIntrinsicHistogram, in, out);

        mRawBytes = bytes;
        mCamera = camera;
        mTimestamp = timestamp;


    }

    @Override
    protected synchronized void weightAllocation() {
        super.getWeightedAllocation();
        aWeighted.copy1DRangeFromUnchecked(0, aWeighted.getBytesSize(), mRawBytes);
        if(mExposureBlock.weights != null) {
            mExposureBlock.weights.forEach_weight(aWeighted, aWeighted);
        }
    }


    @Override
    public boolean claim() {

        if (mBufferClaimed) return true;

        super.claim();

        Mat mat1 = null;
        Mat mat2 = null;

        try {

            byte[] adjustedBytes = new byte[mExposureBlock.res_area * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)];

            // update with weighted pixels
            aWeighted.copyTo(adjustedBytes);

            weightingLock.release();


            // probably a better way to do this, but this
            // works for preventing native memory leaks

            mat1 = new MatOfByte(adjustedBytes);
            mCamera.addCallbackBuffer(adjustedBytes);
            mat2 = mat1.rowRange(0, mExposureBlock.res_area); // only use grayscale byte
            mat1.release();
            mGrayMat = mat2.reshape(1, mExposureBlock.res_y); // create 2D array
            mat2.release();

            mBufferClaimed = true;
        } catch (OutOfMemoryError oom) {
            weightingLock.release();
            if (mat1 != null) mat1.release();
            if (mat2 != null) mat2.release();
            if (mGrayMat != null) mGrayMat.release();
        }

        return mBufferClaimed;
    }

    @Override
    public void retire() {
        super.retire();

        synchronized (mCamera) {
            if (mRawBytes != null) {
                mCamera.addCallbackBuffer(mRawBytes);
                mRawBytes = null;
            }
        }
    }

    @Override
    DataProtos.Event.Builder getEventBuilder() {
        super.getEventBuilder();

        mEventBuilder.setTimestampTarget(mTimestamp);
        return mEventBuilder;
    }

}
