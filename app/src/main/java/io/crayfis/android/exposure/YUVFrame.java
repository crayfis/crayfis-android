package io.crayfis.android.exposure;

import android.graphics.ImageFormat;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.daq.AcquisitionTime;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 9/2/2017.
 */

public class YUVFrame extends Frame {

    // RenderScript objects
    private final ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    private final ScriptC_weight mScriptCWeight;
    private final Allocation aWeighted;

    YUVFrame(@NonNull final Allocation alloc,
             final TotalCaptureResult result,
             final Frame.Producer producer,
             final AcquisitionTime acquisitionTime,
             final Location location,
             final float[] orientation,
             final float rotationZZ,
             final float pressure,
             final ExposureBlock exposureBlock,
             final int resX,
             final int resY,
             final ScriptC_weight scriptCWeight,
             final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
             final Allocation weighted,
             final Allocation hist,
             final Lock histLock) {

        super(alloc, result, producer, acquisitionTime, location, orientation, rotationZZ,
                pressure, exposureBlock, resX, resY, hist, histLock);

        mFormat = Format.YUV;

        mScriptCWeight = scriptCWeight;
        mScriptIntrinsicHistogram = scriptIntrinsicHistogram;
        aWeighted = weighted;
    }

    @Override
    int[] histogram() {

        int[] hist = new int[256];

        // apply weights if necessary
        mHistLock.lock();

        if(mExposureBlock.weights != null) {
            mScriptCWeight.set_gIn(aBuf);
            mScriptCWeight.forEach_weightYUV(mExposureBlock.weights, aWeighted);
            mScriptIntrinsicHistogram.forEach(aWeighted);
        } else {
            mScriptIntrinsicHistogram.forEach(aBuf);
        }

        aHist.copyTo(hist);
        mHistLock.unlock();

        return hist;
    }


    /**
     * Surface generator for YUV frames
     */
    static class Producer extends Frame.Producer implements Allocation.OnBufferAvailableListener {
        static final String TAG = "YuvFrameProducer";

        private static final AtomicInteger nBuffersQueued = new AtomicInteger();
        private Allocation aReady; // already called ioReceive()
        private final Allocation aBurner; // receives buffers to be dropped

        private static final int MAX_BUFFERS = 2;

        Producer(RenderScript rs,
                 Size size,
                 OnFrameCallback callback,
                 Handler handler,
                 Frame.Builder builder) {
            
            super(rs, size, callback, handler, builder);

            // all of mAlloc should have the same surface
            aBurner = mAllocs.peek();
            aBurner.setOnBufferAvailableListener(this);
            mSurfaces.add(aBurner.getSurface());

            nBuffersQueued.set(0);
        }

        @Override
        Allocation[] buildAllocs(Size sz, RenderScript rs, int n) {
            Type t = new Type.Builder(rs, Element.U8(rs))
                    .setX(sz.getWidth())
                    .setY(sz.getHeight())
                    .setYuvFormat(ImageFormat.YUV_420_888)
                    .create();

            // make an extra for the burner allocation
            return Allocation.createAllocations(rs, t,
                    Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_INPUT, n+1);
        }

        // Callback for Buffer Available:
        @Override
        public void onBufferAvailable(final Allocation alloc) {
            if(nBuffersQueued.get() > MAX_BUFFERS) {
                aBurner.ioReceive();
                mDroppedImages++;
            } else {
                nBuffersQueued.incrementAndGet();
            }
            //CFLog.d("onBufferAvailable() " + nBuffersQueued.get());
            buildFrames();
        }

        synchronized void buildFrames() {

            while ((nBuffersQueued.get() > 0 && !mAllocs.isEmpty()) || aReady != null) {

                if(aReady == null) {
                    aReady = mAllocs.poll();
                    aReady.ioReceive();
                    nBuffersQueued.decrementAndGet();
                }

                long allocTimestamp = aReady.getTimeStamp();

                try {
                    Pair<TotalCaptureResult, AcquisitionTime> pair = mResultCollector.findMatch(allocTimestamp);

                    if (pair == null) {
                        // wait for a CaptureResult
                        return;
                    }

                    // we have a match, so build a frame
                    TotalCaptureResult result = pair.first;
                    AcquisitionTime t = pair.second;

                    dispatchFrame(FRAME_BUILDER.setCapture(aReady, result)
                            .setAcquisitionTime(t)
                            .build());

                    aReady = null;

                } catch (CaptureResultCollector.StaleTimeStampException e) {
                    Log.e(TAG, "stale allocation timestamp encountered, discarding image.");
                    mDroppedImages++;
                }
            }
        }
    }

}
