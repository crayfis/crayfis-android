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

import io.crayfis.android.ScriptC_yuv;
import io.crayfis.android.daq.AcquisitionTime;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 9/2/2017.
 */

public class YUVFrame extends Frame {

    // RenderScript objects
    private final ScriptIntrinsicHistogram mScriptIntrinsicHistogram;
    private final ScriptC_yuv mScriptCYuv;
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
             final ScriptC_yuv scriptCYuv,
             final ScriptIntrinsicHistogram scriptIntrinsicHistogram,
             final Allocation weighted,
             final Allocation hist,
             final Lock histLock) {

        super(alloc, result, producer, acquisitionTime, location, orientation, rotationZZ,
                pressure, exposureBlock, resX, resY, hist, histLock);

        // first load grayscale data into aBuf so ain can receive another buffer
        // N.B. since buildFrames() is synchronized, the next ioReceive() will be called after
        // this terminates, and RS will respect this synchronization
        scriptCYuv.forEach_grayscale(alloc);

        mFormat = Format.YUV;

        mScriptCYuv = scriptCYuv;
        mScriptIntrinsicHistogram = scriptIntrinsicHistogram;
        aWeighted = weighted;
    }

    @Override
    int[] histogram() {

        int[] hist = new int[256];

        // apply weights if necessary

        if(mExposureBlock.weights != null) {
            mScriptCYuv.forEach_weightYUV(aBuf, mExposureBlock.weights, aWeighted);
            mScriptIntrinsicHistogram.forEach(aWeighted);
        } else {
            mScriptIntrinsicHistogram.forEach(aBuf);
        }

        aHist.copyTo(hist);

        return hist;
    }

    @Override
    public void copyRange(int xOffset, int yOffset, int w, int h, short[] array) {
        byte[] buf = new byte[w*h];
        aBuf.copy2DRangeTo(xOffset, yOffset, w, h, buf);
        for(int i=0; i<buf.length; i++) {
            array[i] = (short) (buf[i] & 0xFF);
        }
    }


    /**
     * Surface generator for YUV frames
     */
    static class Producer extends Frame.Producer implements Allocation.OnBufferAvailableListener {
        static final String TAG = "YuvFrameProducer";

        private static final AtomicInteger nBuffersQueued = new AtomicInteger();
        private boolean aReady; // already called ioReceive()
        final Allocation ain; // receives buffers to be converted to grayscale

        private static final int MAX_BUFFERS = 2;

        Producer(RenderScript rs,
                 Size size,
                 OnFrameCallback callback,
                 Handler handler,
                 Frame.Builder builder) {
            
            super(rs, size, callback, handler, builder);

            ain = Allocation.createTyped(rs, new Type.Builder(rs, Element.U8(rs))
                    .setX(size.getWidth())
                    .setY(size.getHeight())
                    .setYuvFormat(ImageFormat.YUV_420_888)
                    .create(),
                    Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

            mSurfaces.add(ain.getSurface());

            nBuffersQueued.set(0);
            handler.postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    ain.setOnBufferAvailableListener(Producer.this);
                }
            });
        }

        @Override
        Allocation[] buildAllocs(Size sz, RenderScript rs, int n) {
            Type t = new Type.Builder(rs, Element.U8(rs))
                    .setX(sz.getWidth())
                    .setY(sz.getHeight())
                    .create();

            return Allocation.createAllocations(rs, t,
                    Allocation.USAGE_SCRIPT, n);
        }

        // Callback for Buffer Available:
        @Override
        public void onBufferAvailable(final Allocation alloc) {
            if(nBuffersQueued.get() > MAX_BUFFERS) {
                // make sure this isn't called during buildFrames()
                synchronized (this) {
                    ain.ioReceive();
                }
                mCallback.onDropped();
            } else {
                nBuffersQueued.incrementAndGet();
            }
            //CFLog.d("onBufferAvailable() " + nBuffersQueued.get());
            buildFrames();
        }

        synchronized void buildFrames() {

            while ((nBuffersQueued.get() > 0 || aReady) && !mAllocs.isEmpty()) {

                if(!aReady) {
                    ain.ioReceive();
                    aReady = true;
                    nBuffersQueued.decrementAndGet();
                }

                long allocTimestamp = ain.getTimeStamp();

                try {
                    Pair<TotalCaptureResult, AcquisitionTime> pair = mResultCollector.findMatch(allocTimestamp);

                    if (pair == null) {
                        // wait for a CaptureResult
                        return;
                    }

                    // we have a match, so build a frame
                    TotalCaptureResult result = pair.first;
                    AcquisitionTime t = pair.second;

                    dispatchFrame(FRAME_BUILDER.setCapture(mAllocs.pop(), result)
                            .setAcquisitionTime(t)
                            .build());

                } catch (CaptureResultCollector.StaleTimeStampException e) {
                    Log.e(TAG, "stale allocation timestamp encountered, discarding image.");
                    mCallback.onDropped();
                }

                aReady = false;
            }
        }

        @Override
        public void close() {
            super.close();
            ain.destroy();
        }
    }

}
