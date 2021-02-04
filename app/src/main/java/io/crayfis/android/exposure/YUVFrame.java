package io.crayfis.android.exposure;

import android.graphics.ImageFormat;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.os.Build;
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

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
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
             final Semaphore lock,
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

        super(alloc, result, lock, acquisitionTime, location, orientation, rotationZZ,
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

        private final Deque<Allocation> allocationQueue = new ConcurrentLinkedDeque<>();

        Producer(RenderScript rs, 
                 int maxAllocations, 
                 Size size,
                 OnFrameCallback callback,
                 Handler handler,
                 Frame.Builder builder) {
            
            super(rs, maxAllocations, size, callback, handler, builder);
            for (Allocation a : mAllocs) {
                a.setOnBufferAvailableListener(this);
                mSurfaces.add(a.getSurface());
            }
        }

        @Override
        Allocation buildAlloc(Size sz, RenderScript rs) {
            return Allocation.createTyped(rs, new Type.Builder(rs, Element.U8(rs))
                            .setX(sz.getWidth())
                            .setY(sz.getHeight())
                            .setYuvFormat(ImageFormat.YUV_420_888)
                            .create(),
                    Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_INPUT);
        }

        // Callback for Buffer Available:
        @Override
        public void onBufferAvailable(final Allocation alloc) {
            allocationQueue.add(alloc);
            //CFLog.d("onBufferAvailable() " + allocationQueue.size());
            buildFrames();
        }

        synchronized void buildFrames() {

            while (!allocationQueue.isEmpty()) {

                Allocation alloc = allocationQueue.poll();
                final Semaphore lock = mLocks.get(alloc);

                synchronized (lock) {
                    /*
                     Set two locks, one to be removed by creating the frame and the other
                     by retiring it.  This distinguishes between an Allocation awaiting a
                     CaptureResult to be packaged as a Frame and an Allocation still being
                     used by a Frame
                     */
                    switch (lock.availablePermits()) {
                        case 2:
                            // the Allocation is free and empty, so acquire the new buffer
                            lock.acquireUninterruptibly(2);
                            alloc.ioReceive();
                            break;
                        case 1:
                            // this Allocation is still held by another frame, so wait
                            allocationQueue.offerFirst(alloc);
                            return;
                        case 0:
                            // the Allocation is free, but the buffer has already been acquired,
                            // so nothing to do
                            break;
                        default:
                            throw new IllegalMonitorStateException("YUVFrame.Producer has too many locks");
                    }
                }

                long allocTimestamp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        ? alloc.getTimeStamp() : -1;

                // if we can't actually match these, make sure the queue doesn't
                // outgrow CaptureResults
                if((allocTimestamp == -1 || allocTimestamp == 0) && allocationQueue.contains(alloc)) {
                    alloc.ioReceive();
                    allocationQueue.removeFirstOccurrence(alloc);
                }

                try {
                    Pair<TotalCaptureResult, AcquisitionTime> pair = mResultCollector.findMatch(allocTimestamp);

                    if (pair == null) {
                        // re-insert allocation in queue
                        allocationQueue.offerFirst(alloc);
                        return;
                    }

                    // we have a match, so build a frame
                    TotalCaptureResult result = pair.first;
                    AcquisitionTime t = pair.second;
                    lock.release(1);

                    dispatchFrame(FRAME_BUILDER.setCapture(alloc, result, lock)
                            .setAcquisitionTime(t)
                            .build());

                } catch (CaptureResultCollector.StaleTimeStampException e) {
                    Log.e(TAG, "stale allocation timestamp encountered, discarding image.");
                    mDroppedImages++;
                    // this allows the buffer to be overwritten
                    lock.release(2);
                }
            }
        }
    }

}
