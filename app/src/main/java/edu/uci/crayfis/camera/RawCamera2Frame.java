package edu.uci.crayfis.camera;

import android.annotation.TargetApi;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import java.util.concurrent.Semaphore;

import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 9/2/2017.
 */

@TargetApi(21)
class RawCamera2Frame extends RawCameraFrame {

    private Allocation aRaw;

    // lock for buffers entering aRaw
    private static Semaphore mRawLock = new Semaphore(1);

    RawCamera2Frame(@NonNull final Allocation alloc,
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

        aRaw = alloc;
    }

    @Override
    protected synchronized void weightAllocation() {
        super.weightAllocation();
        mRawLock.acquireUninterruptibly();
        aRaw.ioReceive();
        if(mScriptCWeight != null) {
            mScriptCWeight.set_gInYuv(aRaw);
            mScriptCWeight.forEach_weightYuv(aWeighted);
        } else {
            aWeighted = aRaw;
        }
    }

    @Override
    public void claim() {
        mRawBytes = new byte[aRaw.getBytesSize()];
        aRaw.copyTo(mRawBytes);
        mRawLock.release();
        super.claim();
    }

    @Override
    public void retire() {
        super.retire();
        if(!mBufferClaimed) {
            mRawLock.release();
        }
    }

}
