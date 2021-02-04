package io.crayfis.android.exposure;

import android.graphics.ImageFormat;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.crayfis.android.ScriptC_histogramRAW;
import io.crayfis.android.daq.AcquisitionTime;

class RAWFrame extends Frame {

    private final ScriptC_histogramRAW mScriptCHist;

    RAWFrame(@NonNull final Allocation alloc,
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
             final ScriptC_histogramRAW scriptCHist,
             final Allocation hist,
             final Lock histLock) {

        super(alloc, result, lock, acquisitionTime, location, orientation, rotationZZ,
                pressure, exposureBlock, resX, resY, hist, histLock);

        mFormat = Format.RAW;
        mScriptCHist = scriptCHist;
    }

    @Override
    int[] histogram() {

        int[] hist = new int[1024];

        // apply weights if necessary
        mHistLock.lock();
        if(mExposureBlock.weights != null) {
            mScriptCHist.forEach_histogram_weighted(aBuf, mExposureBlock.weights);
        } else {
            mScriptCHist.forEach_histogram_unweighted(aBuf);
        }

        aHist.copyTo(hist);
        mScriptCHist.invoke_clear();

        mHistLock.unlock();

        return hist;
    }


    /**
     * Surface generator for RAW frames
     */
    static class Producer extends Frame.Producer implements ImageReader.OnImageAvailableListener {
        private static final String TAG = "RawFrameProducer";
        private static final int MAX_IMAGES = 3;

        private static final AtomicInteger nImagesOutstanding = new AtomicInteger();

        private ImageReader mImageReader;
        // Storage for last acquired image
        private final Deque<Image> mImageQueue = new ConcurrentLinkedDeque<>();
        private final short[] mShortArrayBuf;
        private final Lock mShortArrayLock = new ReentrantLock();

        Producer(RenderScript rs,
                 int maxAllocations,
                 Size size,
                 OnFrameCallback callback,
                 Handler handler,
                 Frame.Builder builder) {
            
            super(rs, maxAllocations, size, callback, handler, builder);

            mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.RAW_SENSOR, MAX_IMAGES);
            mImageReader.setOnImageAvailableListener(this, handler);

            mShortArrayBuf = new short[size.getWidth() * size.getHeight()];

            mSurfaces.add(mImageReader.getSurface());
            nImagesOutstanding.set(0);
        }

        @Override
        Allocation buildAlloc(Size sz, RenderScript rs) {
            return Allocation.createTyped(rs, new Type.Builder(rs, Element.U16(rs))
                            .setX(sz.getWidth())
                            .setY(sz.getHeight())
                            .create(),
                    Allocation.USAGE_SCRIPT);
        }


        @Override
        synchronized void buildFrames() {
            while(!mImageQueue.isEmpty()) {
                nImagesOutstanding.incrementAndGet();
                Image i = mImageQueue.poll();
                try {
                    Pair<TotalCaptureResult, AcquisitionTime> pair = mResultCollector.findMatch(i.getTimestamp());

                    if(pair == null) {
                        // re-insert the Image in queue
                        mImageQueue.offerFirst(i);
                        nImagesOutstanding.decrementAndGet();
                        break;
                    }

                    TotalCaptureResult result = pair.first;
                    AcquisitionTime t = pair.second;

                    // cycle through allocations
                    Allocation alloc = mAllocs.poll();
                    Semaphore lock = mLocks.get(alloc);
                    mAllocs.add(alloc);

                    // insert the image buffer into an Allocation
                    Image.Plane plane = i.getPlanes()[0];
                    ByteBuffer buf = plane.getBuffer();

                    ShortBuffer sbuf = buf.asShortBuffer();
                    //short[] vals = new short[sbuf.capacity()];
                    mShortArrayLock.lock();
                    sbuf.get(mShortArrayBuf);
                    i.close();
                    nImagesOutstanding.decrementAndGet();

                    synchronized (mLocks) {
                        if (lock.availablePermits() > 0) {
                            lock.acquireUninterruptibly();
                            alloc.copyFromUnchecked(mShortArrayBuf);
                            mShortArrayLock.unlock();
                        } else {
                            mDroppedImages++;
                            mShortArrayLock.unlock();
                            return;
                        }
                    }

                    dispatchFrame(FRAME_BUILDER.setCapture(alloc, result, lock)
                            .setAcquisitionTime(t)
                            .build());

                } catch (CaptureResultCollector.StaleTimeStampException e) {
                    i.close();
                    nImagesOutstanding.decrementAndGet();
                    mDroppedImages++;
                }
            }
        }

        // Callback for Image Available
        @Override
        public void onImageAvailable(ImageReader mImageReader) {
            if(mImageQueue.size() + nImagesOutstanding.get() >= MAX_IMAGES) {
                if(mImageQueue.size() == 0) return;
                mImageQueue.poll()
                        .close();
                mDroppedImages++;
            }

            mImageQueue.offer(mImageReader.acquireNextImage());
            buildFrames();
        }

        @Override
        public void close(){
            super.close();
            if(mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        }

    }
    
    

}
