package io.crayfis.android.camera;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.renderscript.Allocation;
import android.support.annotation.Nullable;
import android.util.Size;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 5/17/16.
 */
public class ResolutionSpec {
    @Nullable
    public static ResolutionSpec fromString(String spec) {
        if (spec == null) {
            return null;
        }

        int w = 0;
        int h = 0;

        // first, see if it's one of the "named" resolutions
        switch (spec.toLowerCase()) {
            case "low":
                return new ResolutionSpec(320, 240, "low");
            case "medium":
                return new ResolutionSpec(640, 480, "medium");
            case "720p":
                return new ResolutionSpec(1280, 720, "720p");
            case "1080p":
                return new ResolutionSpec(1920, 1080, "1080p");
            case "1440p":
                return new ResolutionSpec(2560, 1440, "1440p");
            case "2160p":
                return new ResolutionSpec(3840, 2160, "2160p");
            case "raw":
                return new ResolutionSpec(Integer.MAX_VALUE, 1, "raw");
            default:
                break;
        }

        // else, assume it's specified in "WxH" format:
        String[] vals = spec.split("x", 2);
        if (vals.length != 2) {
            return null;
        }

        try {
            w = Integer.parseInt(vals[0]);
            h = Integer.parseInt(vals[1]);
        } catch(NumberFormatException e) {
            return null;
        }

        return new ResolutionSpec(w,h);
    }

    public final int width;
    public final int height;
    public final String name;

    public ResolutionSpec(int w, int h) {
        width = w;
        height = h;
        name = "";
    }

    public ResolutionSpec(int w, int h, String name) {
        width = w;
        height = h;
        this.name = name;
    }

    public String toString() {
        return width+"x"+height;
    }

    public Camera.Size getClosestSize(Camera camera) {
        List<Camera.Size> availableSizes = camera.getParameters().getSupportedPreviewSizes();
        if (availableSizes.isEmpty()) {
            CFLog.e("Camera reports no available sizes!");
            return null;
        }

        // sort to match the total # of pixels in the requested spec.
        final int targetArea = width*height;
        Collections.sort(availableSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size s0, Camera.Size s1) {
                return Math.abs(targetArea - s0.height*s0.width) - Math.abs(targetArea - s1.height*s1.width);
            }
        });

        // return the elt with the smallest difference from the requested # of pixels.
        return availableSizes.get(0);
    }

    @TargetApi(21)
    public Size getClosestSize(CameraCharacteristics cc) {
        StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(map != null) {
            Size[] outputSizes = map.getOutputSizes(Allocation.class);
            List<Size> availableSizes = Arrays.asList(outputSizes);
            // sort to match the total # of pixels in the requested spec.
            final int targetArea = width*height;
            Collections.sort(availableSizes, new Comparator<Size>() {
                @Override
                public int compare(Size s0, Size s1) {
                    return Math.abs(targetArea - s0.getWidth()*s0.getHeight())
                            - Math.abs(targetArea - s1.getWidth()*s1.getHeight());
                }
            });

            // return the elt with the smallest difference from the requested # of pixels.
            return availableSizes.get(0);
        }

        return null;
    }
}
