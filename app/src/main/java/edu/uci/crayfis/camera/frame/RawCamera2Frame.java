package edu.uci.crayfis.camera.frame;

import android.annotation.TargetApi;
import android.location.Location;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicHistogram;
import android.support.annotation.NonNull;

import java.util.concurrent.Semaphore;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.AcquisitionTime;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.trigger.L1Processor;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 9/2/2017.
 */

@TargetApi(21)
class RawCamera2Frame extends RawCameraFrame {

    private Allocation aRaw;

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

        if(exposureBlock.daq_state == CFApplication.State.DATA) {
            mRawLock.acquireUninterruptibly();
        }
        aRaw = alloc;
        aRaw.ioReceive();
    }

    @Override
    public synchronized Allocation getWeightedAllocation() {
        super.getWeightedAllocation();
        if(mScriptCWeight != null) {
            mScriptCWeight.set_gInYuv(aRaw);
            mScriptCWeight.forEach_weightYuv(aWeighted);
        } else {
            aWeighted = aRaw;
        }
        return aWeighted;
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
        if(mRawLock.availablePermits() == 0) {
            mRawLock.release();
        }
        super.retire();
    }

}
