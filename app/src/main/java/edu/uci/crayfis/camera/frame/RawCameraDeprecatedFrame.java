package edu.uci.crayfis.camera.frame;

import android.hardware.Camera;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.AcquisitionTime;
import edu.uci.crayfis.exposure.ExposureBlock;

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
                           final int batteryTemp,
                           final ExposureBlock exposureBlock,
                           final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                           final ScriptC_weight scriptCWeight,
                           final Allocation in,
                           final Allocation out) {

        super(cameraId, facingBack, frameWidth, frameHeight, length, acquisitionTime, timestamp,
                location, orientation, rotationZZ, pressure, batteryTemp, exposureBlock, scriptIntrinsicHistogram,
                scriptCWeight, in, out);

        mRawBytes = bytes;
        mCamera = camera;
    }


    @Override
    public synchronized Allocation getWeightedAllocation() {
        aWeighted.copy1DRangeFromUnchecked(0, mLength, mRawBytes);
        if(mScriptCWeight != null) {
            mScriptCWeight.forEach_weight(aWeighted, aWeighted);
        }
        return super.getWeightedAllocation();
    }


    @Override
    public Mat getGrayMat() {

        if(mGrayMat == null) {

            //FIXME: this is way too much copying
            byte[] adjustedBytes = new byte[mRawBytes.length];

            // update with weighted pixels
            aWeighted.copyTo(adjustedBytes);

            lock.unlock();

            // probably a better way to do this, but this
            // works for preventing native memory leaks

            Mat mat1 = new MatOfByte(adjustedBytes);
            Mat mat2 = mat1.rowRange(0, mLength); // only use grayscale byte
            mat1.release();
            mGrayMat = mat2.reshape(1, mFrameHeight); // create 2D array
            mat2.release();

            mCamera.addCallbackBuffer(adjustedBytes);
        }

        return super.getGrayMat();
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
