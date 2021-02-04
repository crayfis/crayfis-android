package io.crayfis.android.exposure;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.crayfis.android.DataProtos;
import io.crayfis.android.ScriptC_histogramRAW;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.daq.AcquisitionTime;
import io.crayfis.android.util.CFLog;

/**
 * Representation of a single frame from the camera.  This tracks the image data along with the
 * location of the device and the time the frame was captured.
 */
public abstract class Frame {
    
    public enum Format {
        RAW,
        YUV,
    }

    protected Format mFormat;

    final Allocation aBuf;
    final TotalCaptureResult mResult;
    final Semaphore mAllocLock;

    private int[] mHist;

    // locks for copying data in RenderScript context

    // these are kept as instance variables in case the scripts
    // are rebuilt by the Builder (e.g. for changing cameras)
    final Lock mHistLock;

    final Allocation aHist;

    // these are left as frame properties in case they change in a SURVEY block
    final int mResX;
    final int mResY;

    private final AcquisitionTime mAcquiredTime;
    private final Location mLocation;
    private final float[] mOrientation;
    private final float mRotationZZ;
    private final float mPressure;

    final ExposureBlock mExposureBlock;

    int mPixMax = -1;
    double mPixAvg = -1;
    double mPixStd = -1;

    DataProtos.Event.Builder mEventBuilder;
    private DataProtos.Event mEvent;
    private boolean mUploadRequested;
    private boolean mCommitted;
    private boolean mRetired;

    public interface OnFrameCallback {
        void onFrame(Frame frame);
    }

    Frame(final Allocation alloc,
          final TotalCaptureResult result,
          final Semaphore allocLock,
          final AcquisitionTime acquisitionTime,
          final Location location,
          final float[] orientation,
          final float rotationZZ,
          final float pressure,
          final ExposureBlock exposureBlock,
          final int resX,
          final int resY,
          final Allocation hist,
          final Lock histLock) {

        aBuf = alloc;
        mResult = result;
        mAllocLock = allocLock;
        mAcquiredTime = acquisitionTime;
        mLocation = location;
        mOrientation = orientation;
        mRotationZZ = rotationZZ;
        mPressure = pressure;
        mExposureBlock = exposureBlock;
        mResX = resX;
        mResY = resY;
        aHist = hist;
        mHistLock = histLock;
    }


    // get raw data in region with inclusive edges at xc +/- dx and yc +/- dy
    // non-existent pixels are set to zero
    public void copyRegion(int xc, int yc, int dx, int dy, short[] array, int offset) {
        int xmin = Math.max(xc - dx, 0);
        int ymin = Math.max(yc - dy, 0);
        int xmax = Math.min(xc + dx, mResX - 1);
        int ymax = Math.min(yc + dy, mResY - 1);
        //Log.d("Frame", "Range:" + xmin + "-" + xmax + ", " + ymin + "-" + ymax);
        int nominal_width = 2 * dx + 1;

        int width = xmax - xmin + 1;
        int height = ymax - ymin + 1;

        short[] buf = new short[width*height];

        copyRange(xmin, ymin, width, height, buf);
        //Log.i("COPY_RANGE_INFO", "xmin = " + xmin + " ; ymin = " + ymin + " ; xmax = " + xmax + " ; ymax = " + ymax + " ; buf = " + buf);

        for (int idy = -dy; idy <= dy; idy++){
            for (int idx = -dx; idx <= dx; idx++) {
                int target_index = (dy+idy)*nominal_width + (dx+idx);
                int x = xc + idx;
                int y = yc + idy;
                if ((x>=xmin)&&(y>=ymin)&&(x<=xmax)&&(y<=ymax)){
                    int alloc_index = (y-ymin)*width + (x-xmin);
                    array[offset+target_index] = buf[alloc_index];
                } else {
                    array[offset+target_index] = -1;
                }
            }
        }
    }

    public void copyRange(int xOffset, int yOffset, int w, int h, short[] array) {
        Object buf = (mFormat == Format.RAW) ? new short[w*h] : new byte[w*h];
        aBuf.copy2DRangeTo(xOffset, yOffset, w, h, buf);
        for(int i=0; i<w*h; i++) {
            array[i] = (mFormat == Format.RAW) ? ((short[]) buf)[i]
                    : (short)(((byte[]) buf)[i] & 0xFF);
        }
    }

    /**
     * Return Allocation of Y/RAW channel
     *
     * @return 2D Allocation
     */
    @Nullable
    public Allocation getAllocation() {
        if(mRetired) return null;
        return aBuf;
    }

    public boolean uploadRequested() {
        return mUploadRequested;
    }

    /**
     * Add frame to ExposureBlock.
     */
    public final synchronized void commit() {
        if(mExposureBlock != null && !mCommitted && mExposureBlock.tryAssignFrame(this)) {
            mCommitted = true;
            mExposureBlock.TRIGGER_CHAIN.submitFrame(this);
        }
    }

    /**
     * Replenish image buffer after sending frame for L2 processing
     */
    @CallSuper
    public boolean claim() {
        // TODO: do we ACTUALLY want to do something here?
        return true;
    }

    /**
     * Return the image buffer to be used by the camera, and free all locks
     */
    @CallSuper
    public synchronized void retire() {
        // make this idempotent
        if(!mRetired) {
            mRetired = true;

            synchronized (mAllocLock) {
                //CFLog.d("Permits: " + this.hashCode() + " " + mAllocLock.availablePermits());
                mAllocLock.release(1);
            }

            if(mCommitted)
                mExposureBlock.clearFrame(this);
        }
    }

    abstract int[] histogram();

    void calculateStatistics() {
        if (mHist != null) {
            // somebody beat us to it! nothing to do.
            return;
        }

        mHist = histogram();

        mExposureBlock.underflow_hist.fill(mHist);

        int max = mHist.length-1;
        int sum = 0;
        double sumDevSq = 0;

        // find max first, so sums are easier
        while(mHist[max] == 0 && max > 0) {
            max--;
        }

        // then find average and standard deviation
        for(int i=0; i<=max; i++) {
            sum += i*mHist[i];
        }

        int npix = mResX * mResY;

        mPixMax = max;
        mPixAvg = (double)sum/npix;

        for(int i=0; i<=max; i++) {
            double dev = i - mPixAvg;
            sumDevSq += mHist[i]*dev*dev;
        }

        mPixStd = Math.sqrt(sumDevSq/(npix-1));
    }

    public Format getFormat() {
        return mFormat;
    }

    public TotalCaptureResult getTotalCaptureResult() {
        return mResult;
    }

    /**
     * Get the epoch time with NTP corrections.
     *
     * @return long
     */
    public long getAcquiredTimeNTP() {
        return mAcquiredTime.NTP;
    }


    /** Get the epoch time.
     *
     * @return long
     */
    public long getAcquiredTime() {
        return mAcquiredTime.Sys;
    }

    /**
     * Get the precision nano time.
     *
     * @return long
     */
    public long getAcquiredTimeNano() { return mAcquiredTime.Nano; }


    /**
     * Get the location for this.
     *
     * @return {@link android.location.Location}
     */
    public Location getLocation() {
        return mLocation;
    }

    /**
     * Get the orientation of the device when the frame was captured.
     *
     * @return float[]
     */
    public float[] getOrientation() {
        return mOrientation;
    }

    public float getRotationZZ() {
        return mRotationZZ;
    }

    public float getPressure() { return mPressure; }

    public ExposureBlock getExposureBlock() { return mExposureBlock; }

    public int getCameraId() { return mExposureBlock.camera_id; }

    public int getWidth() { return mResX; }

    public int getHeight() { return mResY; }

    public int getPixMax() {
        if (mPixMax < 0) {
            calculateStatistics();
        }
        return mPixMax;
    }

    public double getPixAvg() {
        if (mPixAvg < 0) {
            calculateStatistics();
        }
        return mPixAvg;
    }

    public double getPixStd() {
        if (mPixStd < 0) {
            calculateStatistics();
        }
        return mPixStd;
    }

    public boolean isFacingBack() {
        // if we're getting frames while the camera is off,
        // this deserves an Exception
        return mExposureBlock.camera_facing_back;
    }

    @CallSuper
    DataProtos.Event.Builder getEventBuilder() {
        if (mEventBuilder == null) {
            mEventBuilder = DataProtos.Event.newBuilder();

            Long timestamp = mResult.get(CaptureResult.SENSOR_TIMESTAMP);
            if(timestamp != null) {
                mEventBuilder.setTimestampTarget(timestamp);
            }

            Long exposureTime = mResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if(exposureTime != null) {
                mEventBuilder.setExposureTime(exposureTime);
            }

            mEventBuilder.setTimestamp(getAcquiredTime())
                    .setTimestampNano(getAcquiredTimeNano())
                    .setTimestampNtp(getAcquiredTimeNTP())
                    .setPressure(mPressure)
                    .setGpsLat(mLocation.getLatitude())
                    .setGpsLon(mLocation.getLongitude())
                    .setGpsFixtime(mLocation.getTime())
                    .setAvg(getPixAvg())
                    .setStd(getPixStd());
            if (mLocation.hasAccuracy()) {
                mEventBuilder.setGpsAccuracy(mLocation.getAccuracy());
            }
            if (mLocation.hasAltitude()) {
                mEventBuilder.setGpsAltitude(mLocation.getAltitude());
            }
            if(mOrientation != null) {
                mEventBuilder.setOrientX(mOrientation[0])
                        .setOrientY(mOrientation[1])
                        .setOrientZ(mOrientation[2]);
            }

            for (int val=0; val < mExposureBlock.underflow_hist.size(); val++) {
                mEventBuilder.addHist(mHist[val]);
            }

            mEventBuilder.setXbn(mExposureBlock.xbn);
        }

        return mEventBuilder;
    }

    public void setPixels(List<DataProtos.Pixel> pixels) {
        mEvent = null;
        mUploadRequested = true;
        getEventBuilder().addAllPixels(pixels);
    }

    public void setByteBlock(DataProtos.ByteBlock byteBlock) {
        mEvent = null;
        mUploadRequested = true;
        getEventBuilder().setByteBlock(byteBlock);
    }

    public void setZeroBias(DataProtos.ZeroBiasSquare zeroBiasSquare) {
        mEvent = null;
        mUploadRequested = true;
        getEventBuilder().setZeroBias(zeroBiasSquare);
    }

    public DataProtos.Event getEvent() {
        if(mEvent == null) {
            mEvent = mEventBuilder.build();
        }
        return mEvent;
    }

    /**
     * Class for synchronizing CaptureResults and frame buffers
     */
    public abstract static class Producer extends CameraCaptureSession.CaptureCallback {
        // externally provided via constructor:
        final int mMaxAllocations;
        final OnFrameCallback mCallback;
        final Handler mFrameHandler;
        
        final Builder FRAME_BUILDER;

        // Collection of recent TotalCaptureResults
        final CaptureResultCollector mResultCollector = new CaptureResultCollector();

        final Queue<Allocation> mAllocs;
        final List<Surface> mSurfaces;
        final HashMap<Allocation, Semaphore> mLocks;

        // statistics on frame building
        int mDroppedImages = 0;
        int mMatches = 0;

        // stop has been called
        boolean mStopCalled = false;

        Producer(RenderScript rs,
                 int maxAllocations,
                 Size sz,
                 OnFrameCallback callback,
                 Handler handler,
                 Builder builder) {
            
            mMaxAllocations = maxAllocations;
            mCallback = callback;
            mFrameHandler = handler;
            FRAME_BUILDER = builder;

            mAllocs = new ArrayBlockingQueue<>(maxAllocations);
            mSurfaces = new ArrayList<>(maxAllocations);
            mLocks = new HashMap<>();

            for(int i=0; i<maxAllocations; i++) {
                Allocation a = buildAlloc(sz, rs);
                mAllocs.add(a);
                mLocks.put(a, new Semaphore(2));
            }
        }

        public static Producer create(boolean raw, 
                                      RenderScript rs, 
                                      int maxAllocations, 
                                      Size sz,
                                      OnFrameCallback callback,
                                      Handler handler,
                                      Builder builder) {
            if(raw) {
                return new RAWFrame.Producer(rs, maxAllocations, sz, callback, handler, builder);
            } else {
                return new YUVFrame.Producer(rs, maxAllocations, sz, callback, handler, builder);
            }
        }

        /**
         * Assemble an Allocation buffer to fit the Frame data
         *
         * @param sz Resolution Size
         * @param rs RenderScript context
         * @return RenderScript Allocation
         */
        abstract Allocation buildAlloc(Size sz, RenderScript rs);

        /**
         * Attempt to package TotalCaptureResults and buffers into a
         * Frames.
         */
        abstract void buildFrames();

        /**
         * Pass a frame to mCallback through mFrameHandler
         *
         * @param frame the Frame to be submitted
         */
        final void dispatchFrame(final Frame frame) {
            if(frame != null) {
                if(mStopCalled) {
                    frame.retire();
                    return;
                }

                mMatches++;
                
                // operate in the same thread as the CaptureCallback
                mFrameHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onFrame(frame);
                    }
                });
            }
        }

        /**
         * List surfaces onto which buffers can be cast
         *
         * @return List of Surfaces
         */
        public final List<Surface> getSurfaces() {
            return mSurfaces;
        }

        @CallSuper
        public void close(){
            mStopCalled = true;
            for (Surface s : mSurfaces) {
                s.release();
            }
            mSurfaces.clear();
            for (Allocation a : mAllocs) {
                a.destroy();
            }
            mAllocs.clear();
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            mResultCollector.add(result, new AcquisitionTime());
            buildFrames();
        }
    }

    /**
     * Class for creating immutable RawCameraFrames
     */
    public static class Builder {

        private Format bFormat;

        private Allocation aBuf;
        private TotalCaptureResult bResult;
        private Semaphore bAllocLock;

        private int bResX;
        private int bResY;

        private AcquisitionTime bAcquisitionTime;
        private Location bLocation;
        private float[] bOrientation;
        private float bRotationZZ;
        private float bPressure;
        private ExposureBlock bExposureBlock;
        
        private Lock bHistLock;

        private ScriptC_histogramRAW bScriptCHistogram;
        private ScriptC_weight bScriptCWeight;
        private ScriptIntrinsicHistogram bScriptIntrinsicHistogram;

        private Allocation bWeighted;
        private Allocation bHist;

        public Builder setCapture(Allocation buf, TotalCaptureResult result, Semaphore lock) {
            aBuf = buf;
            bResult = result;
            bAllocLock = lock;
            return this;
        }


        public Builder configureRAW(RenderScript rs, Size sz) {
            bFormat = Format.RAW;

            bResX = sz.getWidth();
            bResY = sz.getHeight();

            bScriptCHistogram = new ScriptC_histogramRAW(rs);
            bHist = Allocation.createSized(rs, Element.U32(rs), 1024, Allocation.USAGE_SCRIPT);

            bScriptCHistogram.bind_ahist(bHist);

            // build a lock for this script
            bHistLock = new ReentrantLock();

            // get rid of YUV objects
            if(bScriptIntrinsicHistogram != null) {
                bScriptIntrinsicHistogram.destroy();
                bScriptCWeight.destroy();
                bWeighted.destroy();

                bScriptIntrinsicHistogram = null;
                bScriptCWeight = null;
                bWeighted = null;
            }

            return this;
        }

        public Builder configureYUV(RenderScript rs, Size sz) {
            bFormat = Format.YUV;

            bResX = sz.getWidth();
            bResY = sz.getHeight();

            Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
            Type type = tb.setX(bResX)
                    .setY(bResY)
                    .create();

            bScriptCWeight = new ScriptC_weight(rs);
            bScriptIntrinsicHistogram = ScriptIntrinsicHistogram.create(rs, Element.U8(rs));

            bWeighted = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT);
            bHist = Allocation.createSized(rs, Element.U32(rs), 256, Allocation.USAGE_SCRIPT);

            bScriptIntrinsicHistogram.setOutput(bHist);

            // build a lock for these scripts
            bHistLock = new ReentrantLock();

            // get rid of RAW objects
            if(bScriptCHistogram != null) {
                bScriptCHistogram.destroy();
                bScriptCHistogram = null;
            }

            return this;
        }

        public Builder setAcquisitionTime(AcquisitionTime acquisitionTime) {
            bAcquisitionTime = acquisitionTime;
            return this;
        }

        public Builder setLocation(Location location) {
            bLocation = location;
            return this;
        }

        public Builder setOrientation(float[] orientation) {
            bOrientation = orientation;
            return this;
        }

        public Builder setRotationZZ(float rotationZZ) {
            bRotationZZ = rotationZZ;
            return this;
        }

        public Builder setPressure(float pressure) {
            bPressure = pressure;
            return this;
        }

        public Builder setExposureBlock(ExposureBlock exposureBlock) {
            bExposureBlock = exposureBlock;
            return this;
        }

        public Frame build() {
            switch (bFormat) {
                case YUV:
                    return new YUVFrame(aBuf, bResult, bAllocLock, bAcquisitionTime, bLocation,
                            bOrientation, bRotationZZ, bPressure, bExposureBlock, bResX, bResY,
                            bScriptCWeight, bScriptIntrinsicHistogram, bWeighted, bHist, bHistLock);
                case RAW:
                    return new RAWFrame(aBuf, bResult, bAllocLock, bAcquisitionTime, bLocation,
                            bOrientation, bRotationZZ, bPressure, bExposureBlock, bResX, bResY,
                            bScriptCHistogram, bHist, bHistLock);
                default:
                    return null;
            }
        }

    }

}
