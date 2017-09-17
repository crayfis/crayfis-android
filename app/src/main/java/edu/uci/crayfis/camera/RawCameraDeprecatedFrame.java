package edu.uci.crayfis.camera;

import android.hardware.Camera;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import edu.uci.crayfis.ScriptC_weight;
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

        super(cameraId, facingBack, frameWidth, frameHeight, length, bufferSize, acquisitionTime, timestamp,
                location, orientation, rotationZZ, pressure, batteryTemp, exposureBlock, scriptIntrinsicHistogram,
                scriptCWeight, in, out);

        mRawBytes = bytes;
        mCamera = camera;
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
    public void claim() {
        mCamera.addCallbackBuffer(super.createMatAndReturnBuffer());
        mBufferClaimed = true;
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
