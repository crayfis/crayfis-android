package edu.uci.crayfis.camera;

import android.hardware.Camera;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.uci.crayfis.util.CFLog;

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
}
