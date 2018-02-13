package io.crayfis.android.exposure.frame;

import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import io.crayfis.android.ScriptC_parse;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 12/10/17.
 */

class RawCamera2RAWFrame extends RawCameraFrame {

    private short[] mRawShorts;
    private Allocation aRaw;
    private final ScriptC_parse mScriptCParse;

    RawCamera2RAWFrame(@NonNull short[] shorts,
                       @NonNull final Allocation ain,
                       final int cameraId,
                       final boolean facingBack,
                       final int frameWidth,
                       final int frameHeight,
                       final int length,
                       @NonNull final AcquisitionTime acquisitionTime,
                       final long timestamp,
                       final Location location,
                       final float[] orientation,
                       final float rotationZZ,
                       final float pressure,
                       @NonNull final ExposureBlock exposureBlock,
                       @NonNull final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                       final ScriptC_weight scriptCWeight,
                       @NonNull final ScriptC_parse scriptCParse,
                       @NonNull final Allocation weighted,
                       @NonNull final Allocation out) {

        super(cameraId, facingBack, frameWidth, frameHeight, length, acquisitionTime, timestamp,
                location, orientation, rotationZZ, pressure, exposureBlock, scriptIntrinsicHistogram,
                scriptCWeight, weighted, out);

        mRawShorts = shorts;
        aRaw = ain;
        mScriptCParse = scriptCParse;

    }

    @Override
    protected void weightAllocation() {
        aRaw.copyFrom(mRawShorts);
        if(mScriptCWeight != null) {
            mScriptCWeight.forEach_weightRAW(aRaw, aWeighted);
        } else {
            mScriptCParse.forEach_truncateRAW(aRaw, aWeighted);
        }
    }

    @Override
    public boolean claim() {

        Mat mat1 = null;
        Mat mat2 = null;

        try {

            byte[] adjustedBytes = new byte[aWeighted.getBytesSize()];

            // update with weighted pixels
            aWeighted.copyTo(adjustedBytes);

            weightingLock.release();


            // probably a better way to do this, but this
            // works for preventing native memory leaks

            mat1 = new MatOfByte(adjustedBytes);
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
        mRawShorts = null;
    }

    @Override
    public int getRawValAt(int x, int y) {
        synchronized (mRawShorts) {
            return mRawShorts[x + mFrameWidth * y];
        }
    }

}
