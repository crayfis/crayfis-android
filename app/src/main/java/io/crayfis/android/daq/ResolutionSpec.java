package io.crayfis.android.daq;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.renderscript.Allocation;
import androidx.annotation.Nullable;
import android.util.Size;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by cshimmin on 5/17/16.
 */
public class ResolutionSpec {

    public static final String RAW = "RAW";

    @Nullable
    public static ResolutionSpec fromString(String spec) {
        if (spec == null) {
            return null;
        }

        // first, see if it's one of the "named" resolutions
        switch (spec.toLowerCase()) {
            case "low":
                return new ResolutionSpec(320, 240, "Low");
            case "medium":
                return new ResolutionSpec(640, 480, "Medium");
            case "720p":
                return new ResolutionSpec(1280, 720, "720p");
            case "1080p":
                return new ResolutionSpec(1920, 1080, "1080p");
            case "1440p":
                return new ResolutionSpec(2560, 1440, "1440p");
            case "2160p":
                return new ResolutionSpec(3840, 2160, "2160p");
            case "max":
                return new ResolutionSpec(Integer.MAX_VALUE, 1, "MAX");
            case "raw":
                return new ResolutionSpec(0,0,ResolutionSpec.RAW);
        }

        // else, assume it's specified in "WxH" format:
        String[] vals = spec.split("x", 2);
        if (vals.length != 2) {
            return null;
        }


        try {
            int w = Integer.parseInt(vals[0]);
            int h = Integer.parseInt(vals[1]);
            return new ResolutionSpec(w,h);
        } catch(NumberFormatException e) {
            return null;
        }


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
}
