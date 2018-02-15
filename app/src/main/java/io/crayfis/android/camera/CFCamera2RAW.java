package io.crayfis.android.camera;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Type;
import android.util.Size;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 2/13/18.
 */

@TargetApi(21)
class CFCamera2RAW extends CFCamera2 {

    /**
     * Configures image capture
     */
    @Override
    protected void configureCameraPreviewSession() throws CameraAccessException {

        // first, check whether RAW format is available

        StreamConfigurationMap map = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        String formatString = "Supported formats: ";
        for (int i : map.getOutputFormats()) {
            formatString += i + ", ";
        }
        CFLog.d(formatString);

        int format = ImageFormat.YUV_420_888;

        for (int f : map.getOutputFormats()) {
            if (f == ImageFormat.RAW_SENSOR) {
                format = ImageFormat.RAW_SENSOR;
                break;
            }
        }

        if(format == ImageFormat.YUV_420_888) {
            CFLog.w("No RAW capabilities found: switching to MAX");
            super.configureCameraPreviewSession();
            return;
        }

        mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        configureManualSettings();

        // For RAW captures, we use the largest available size.
        mPreviewSize = Collections.max(
                Arrays.asList(map.getOutputSizes(format)), new Comparator<Size>() {
                    @Override
                    public int compare(Size s1, Size s2) {
                        return s1.getWidth()*s1.getHeight() - s2.getWidth()*s2.getHeight();
                    }
                });

        mResX = mPreviewSize.getWidth();
        mResY = mPreviewSize.getHeight();

        CFLog.d("Size = " + mPreviewSize);

        long minDuration = map.getOutputMinFrameDuration(format, mPreviewSize);
        long targetDuration = Math.max(findTargetDuration(), minDuration);

        CFLog.d("Min duration = " + minDuration + ", target = " + targetDuration);

        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, targetDuration);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, targetDuration);

        ain = Allocation.createTyped(mRS, new Type.Builder(mRS, Element.U8(mRS))
                .setX(mResX)
                .setY(mResY)
                .create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

    }



}
