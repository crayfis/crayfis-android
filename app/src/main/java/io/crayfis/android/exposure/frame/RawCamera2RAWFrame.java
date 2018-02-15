package io.crayfis.android.exposure.frame;

import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 12/10/17.
 */

class RawCamera2RAWFrame extends RawCamera2Frame {

    RawCamera2RAWFrame(@NonNull final Allocation ain,
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
                       @NonNull final Allocation weighted,
                       @NonNull final Allocation out) {

        super(ain, cameraId, facingBack, frameWidth, frameHeight, length, acquisitionTime, timestamp,
                location, orientation, rotationZZ, pressure, exposureBlock, scriptIntrinsicHistogram,
                scriptCWeight, weighted, out);

    }

    @Override
    protected void weightAllocation() {
        if(mScriptCWeight != null) {
            mScriptCWeight.forEach_weight(aRaw, aWeighted);
        } else {
            aWeighted = aRaw;
        }
    }

}
