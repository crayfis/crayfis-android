package io.crayfis.android.camera;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

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
            bBufferSize = bLength * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888);

            bCameraId = cameraId;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            bFacingBack = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK;

            setRenderScript(rs, bFrameWidth, bFrameHeight);
            return this;
        }

        public RawCameraDeprecatedFrame build() {
            return new RawCameraDeprecatedFrame(bBytes, bCamera, bCameraId, bFacingBack,
                    bFrameWidth, bFrameHeight, bLength, bBufferSize, bAcquisitionTime, bTimestamp, bLocation,
                    bOrientation, bRotationZZ, bPressure, bBatteryTemp, bExposureBlock,
                    bScriptIntrinsicHistogram, bScriptCWeight, bWeighted, bOut);
        }

    }

}
