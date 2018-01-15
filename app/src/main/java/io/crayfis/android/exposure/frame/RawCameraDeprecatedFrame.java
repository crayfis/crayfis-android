package io.crayfis.android.exposure.frame;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.exposure.ExposureBlock;

/**
 * Created by Jeff on 9/2/2017.
 */

class RawCameraDeprecatedFrame extends RawCameraFrame {

    private final Camera mCamera;

    RawCameraDeprecatedFrame(@NonNull final byte[] bytes,
                             final Camera camera,
                             final int cameraId,
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

        super(cameraId, facingBack, frameWidth, frameHeight, length, acquisitionTime, timestamp,
                location, orientation, rotationZZ, pressure, exposureBlock, scriptIntrinsicHistogram,
                scriptCWeight, in, out);

        mRawBytes = bytes;
        mCamera = camera;

    }

    @Override
    public void callLocks() {

    }


    @Override
    protected synchronized void weightAllocation() {
        super.getWeightedAllocation();
        aWeighted.copy1DRangeFromUnchecked(0, aWeighted.getBytesSize(), mRawBytes);
        if(mScriptCWeight != null) {
            mScriptCWeight.forEach_weight(aWeighted, aWeighted);
        }
    }


    @Override
    public boolean claim() {
        super.claim();

        if (mBufferClaimed) return true;

        Mat mat1 = null;
        Mat mat2 = null;

        try {

            byte[] adjustedBytes = new byte[mLength * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)];

            // update with weighted pixels
            aWeighted.copyTo(adjustedBytes);

            weightingLock.release();


            // probably a better way to do this, but this
            // works for preventing native memory leaks

            mat1 = new MatOfByte(adjustedBytes);
            mCamera.addCallbackBuffer(adjustedBytes);
            mat2 = mat1.rowRange(0, mLength); // only use grayscale byte
            mat1.release();
            mGrayMat = mat2.reshape(1, mFrameHeight); // create 2D array
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

}
