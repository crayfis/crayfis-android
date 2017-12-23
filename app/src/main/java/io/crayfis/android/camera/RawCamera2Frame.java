package io.crayfis.android.camera;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.support.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import java.util.concurrent.Semaphore;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 9/2/2017.
 */

@TargetApi(21)
class RawCamera2Frame extends RawCameraFrame {

    private Allocation aRaw;

    // lock for buffers entering aRaw
    private static Semaphore mRawLock = new Semaphore(1);

    private RawCamera2Frame(@NonNull final Allocation alloc,
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

        aRaw = alloc;
    }

    public void receiveBytes() {
        mRawLock.acquireUninterruptibly();
        aRaw.ioReceive();
    }

    @Override
    protected synchronized void weightAllocation() {
        if(mScriptCWeight != null) {
            mScriptCWeight.set_gInYuv(aRaw);
            mScriptCWeight.forEach_weightYuv(aWeighted);
        } else {
            aWeighted = aRaw;
        }
    }

    @Override
    public boolean claim() {

        Mat mat1 = null;
        Mat mat2 = null;

        try {
            // use the same byte[] buffer for raw and weighted
            byte[] adjustedBytes = new byte[aRaw.getBytesSize()];
            aWeighted.copyTo(adjustedBytes);
            weightingLock.unlock();
            mat1 = new MatOfByte(adjustedBytes);

            mRawBytes = adjustedBytes;
            aRaw.copyTo(mRawBytes);
            mRawLock.release();

            // probably a better way to do this, but this
            // works for preventing native memory leaks

            mat2 = mat1.rowRange(0, mLength); // only use grayscale byte
            mat1.release();
            mGrayMat = mat2.reshape(1, mFrameHeight); // create 2D array
            mat2.release();
            mBufferClaimed = true;
        } catch (OutOfMemoryError oom) {
            weightingLock.unlock();
            mRawLock.release();
            if (mat1 != null) mat1.release();
            if (mat2 != null) mat2.release();
            if (mGrayMat != null) mGrayMat.release();
        }

        return mBufferClaimed;
    }

    @Override
    public void retire() {
        super.retire();
        if(!mBufferClaimed) {
            mRawLock.release();
        }
    }

    static class Builder extends RawCameraFrame.Builder {

        private Allocation bRaw;

        /**
         * Method for configuring Builder to create RawCamera2Frames
         *
         * @param manager CameraManager
         * @param cameraId int
         * @param alloc Allocation camera buffer
         * @param rs RenderScript context
         * @return Builder
         */
        @TargetApi(21)
        public Builder setCamera2(CameraManager manager, int cameraId, Allocation alloc, RenderScript rs) {

            bRaw = alloc;

            Type type = alloc.getType();

            bFrameWidth = type.getX();
            bFrameHeight = type.getY();
            bLength = bFrameWidth * bFrameHeight;

            bCameraId = cameraId;
            try {
                String[] idList = manager.getCameraIdList();
                CameraCharacteristics cc = manager.getCameraCharacteristics(idList[cameraId]);
                Integer lensFacing = cc.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null) {
                    bFacingBack = (lensFacing == CameraMetadata.LENS_FACING_BACK);
                }
            } catch (CameraAccessException e) {
                CFLog.e("CameraAccessException");
            }

            setRenderScript(rs, bFrameWidth, bFrameHeight);

            return this;
        }

        public RawCamera2Frame build() {
            return new RawCamera2Frame(bRaw, bCameraId, bFacingBack,
                    bFrameWidth, bFrameHeight, bLength, bAcquisitionTime, bTimestamp, bLocation,
                    bOrientation, bRotationZZ, bPressure, bExposureBlock,
                    bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
        }
    }

}