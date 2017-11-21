package io.crayfis.android.camera;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlock;

/**
 * Created by Jeff on 9/2/2017.
 */

class RawCameraDeprecatedFrame extends RawCameraFrame {

    private final Camera mCamera;

    private RawCameraDeprecatedFrame(@NonNull final byte[] bytes,
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
    protected synchronized void weightAllocation() {
        super.getWeightedAllocation();
        aWeighted.copy1DRangeFromUnchecked(0, aWeighted.getBytesSize(), mRawBytes);
        if(mScriptCWeight != null) {
            mScriptCWeight.forEach_weight(aWeighted, aWeighted);
        }
    }


    @Override
    public boolean claim() {

        try {

            byte[] adjustedBytes = new byte[mLength * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)];

            // update with weighted pixels
            aWeighted.copyTo(adjustedBytes);

            weightingLock.unlock();


            // probably a better way to do this, but this
            // works for preventing native memory leaks

            Mat mat1 = new MatOfByte(adjustedBytes);
            mCamera.addCallbackBuffer(adjustedBytes);
            Mat mat2 = mat1.rowRange(0, mLength); // only use grayscale byte
            mat1.release();
            mGrayMat = mat2.reshape(1, mFrameHeight); // create 2D array
            mat2.release();

            mBufferClaimed = true;
        } catch (OutOfMemoryError oom) {
            // TODO: do we need to free native memory?
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

    static class Builder extends RawCameraFrame.Builder {

        private byte[] bBytes;
        private Camera bCamera;

        public Builder setBytes(byte[] bytes) {
            bBytes = bytes;
            return this;
        }

        /**
         * Method for configuring Builder to create RawCameraDeprecatedFrames
         *
         * @param camera Camera
         * @param cameraId int
         * @param rs RenderScript context
         * @return Builder
         */
        public Builder setCamera(Camera camera, int cameraId, RenderScript rs) {

            bCamera = camera;
            Camera.Parameters params = camera.getParameters();
            Camera.Size sz = params.getPreviewSize();
            bFrameWidth = sz.width;
            bFrameHeight = sz.height;
            bLength = bFrameWidth * bFrameHeight;

            bCameraId = cameraId;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            bFacingBack = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK;

            setRenderScript(rs, bFrameWidth, bFrameHeight);
            return this;
        }

        public RawCameraDeprecatedFrame build() {
            return new RawCameraDeprecatedFrame(bBytes, bCamera, bCameraId, bFacingBack,
                    bFrameWidth, bFrameHeight, bLength, bAcquisitionTime, bTimestamp, bLocation,
                    bOrientation, bRotationZZ, bPressure, bExposureBlock,
                    bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
        }

    }

}
