package io.crayfis.android.exposure;

import android.annotation.TargetApi;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.ScriptIntrinsicHistogram;
import androidx.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import java.util.concurrent.Semaphore;

import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.AcquisitionTime;

/**
 * Created by Jeff on 9/2/2017.
 */

@TargetApi(21)
class RawCamera2Frame extends RawCameraFrame {

    private Allocation aRaw;
    private TotalCaptureResult mResult;

    // lock for buffers entering aRaw
    private static final Semaphore mRawLock = new Semaphore(1);

    RawCamera2Frame(@NonNull final Allocation alloc,
                    final AcquisitionTime acquisitionTime,
                    final TotalCaptureResult result,
                    final Location location,
                    final float[] orientation,
                    final float rotationZZ,
                    final float pressure,
                    final ExposureBlock exposureBlock,
                    final int resX,
                    final int resY,
                    final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
                    final Allocation in,
                    final Allocation out) {

        super(acquisitionTime, location, orientation, rotationZZ, pressure, exposureBlock,
                resX, resY, scriptIntrinsicHistogram, in, out);

        aRaw = alloc;
        mResult = result;

    }

    @Override
    void callLocks() {
        mRawLock.acquireUninterruptibly();
        aRaw.ioReceive();
    }

    @Override
    protected synchronized void weightAllocation() {
        if(mExposureBlock.weights != null) {
            if(aRaw.getElement().getDataKind() == Element.DataKind.PIXEL_YUV) {
                mExposureBlock.weights.set_gInYuv(aRaw);
                mExposureBlock.weights.forEach_weightYuv(aWeighted);
            } else {
                mExposureBlock.weights.forEach_weight(aRaw, aWeighted);
            }
        } else {
            aWeighted = aRaw;
        }
    }

    @Override
    public final boolean claim() {

        if (mBufferClaimed) return true;

        super.claim();

        Mat mat1 = null;
        Mat mat2 = null;

        try {
            // use the same byte[] buffer for raw and weighted
            byte[] adjustedBytes = new byte[aRaw.getBytesSize()];
            aWeighted.copyTo(adjustedBytes);
            weightingLock.release();
            mat1 = new MatOfByte(adjustedBytes);

            aRaw.copyTo(adjustedBytes);
            mRawBytes = adjustedBytes;
            mRawLock.release();

            // probably a better way to do this, but this
            // works for preventing native memory leaks

            mat2 = mat1.rowRange(0, mResArea); // only use grayscale byte
            mat1.release();
            mGrayMat = mat2.reshape(1, mResY); // create 2D array
            mat2.release();
            mBufferClaimed = true;
        } catch (OutOfMemoryError oom) {
            if(weightingLock.availablePermits() == 0) weightingLock.release();
            if(mRawLock.availablePermits() == 0) mRawLock.release();
            if (mat1 != null) mat1.release();
            if (mat2 != null) mat2.release();
            if (mGrayMat != null) mGrayMat.release();
        }

        return mBufferClaimed;
    }

    @Override
    public void retire() {
        if(!mBufferClaimed) {
            mRawLock.release();
        }
        super.retire();
    }

    @Override
    public DataProtos.Event.Builder getEventBuilder() {
        super.getEventBuilder();

        Long timestamp = mResult.get(CaptureResult.SENSOR_TIMESTAMP);
        if(timestamp != null) {
            mEventBuilder.setTimestampTarget(timestamp);
        }

        Long exposureTime = mResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if(exposureTime != null) {
            mEventBuilder.setExposureTime(exposureTime);
        }
        return mEventBuilder;
    }

}
