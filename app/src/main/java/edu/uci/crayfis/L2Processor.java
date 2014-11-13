package edu.uci.crayfis;

import android.content.Context;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.exposure.ExposureBlockManager;
import edu.uci.crayfis.particle.ParticleReco;
import edu.uci.crayfis.util.CFLog;

/**
 * External thread used to do more time consuming image processing
 */
class L2Processor extends Thread {

    // Maximum number of raw camera frames to allow on the L2Queue
    private static final int L2Queue_maxFrames = 2;
    // Queue for frames to be processed by the L2 thread
    ArrayBlockingQueue<RawCameraFrame> L2Queue = new ArrayBlockingQueue<RawCameraFrame>(L2Queue_maxFrames);
    // max amount of time to wait on L2Queue (seconds)
    int L2Timeout = 1;


    // ----8< --------


    private int mTotalPixels = 0;
    private int mTotalEvents = 0;
    // This gets set in the data state transition, false in the idle transition
    private boolean mFixedThreshold;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private final ExposureBlockManager XB_MANAGER;
    private final ParticleReco PARTICLE_RECO;

    private long L2counter = 0;
    private long mCalibrationStop;

    // true if a request has been made to stop the thread
    volatile boolean stopRequested = false;
    // true if the thread is running and can process more data
    volatile boolean running = true;

    private final CFApplication APPLICATION;

    /**
     * Create a new instance.
     *
     * @param context Any {@link android.content.Context}, only a reference to {@link CFApplication} will be kept.
     */
    public L2Processor(@NonNull final Context context, @NonNull final Camera.Size cameraSize) {
        APPLICATION = (CFApplication) context.getApplicationContext();
        XB_MANAGER = ExposureBlockManager.getInstance(context);
        PARTICLE_RECO = ParticleReco.getInstance(cameraSize);
    }

    /**
     * Blocks until the thread has stopped
     */
    public void stopThread() {
        stopRequested = true;
        while (running) {
            interrupt();
            Thread.yield();
        }
    }

    // FIXME This is just to be able to move the class out, this should instead be a listener.
    private WeakReference<View> mViewWeakReference;

    /**
     * Set the view that should be invalidated when data has been processed.
     *
     * @param view The view.
     */
    public void setView(@Nullable final View view) {
        if (mViewWeakReference !=  null) {
            mViewWeakReference.clear();
        }
        if (view != null) {
            mViewWeakReference = new WeakReference<View>(view);
        }
    }

    @Override
    public void run() {

        while (!stopRequested) {
            boolean interrupted = false;

            RawCameraFrame frame = null;
            try {
                // Grab a frame buffer from the queue, blocking if none
                // is available.
                frame = L2Queue.poll(L2Timeout, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex) {
                // Interrupted, possibly by app shutdown?
                CFLog.d("DAQActivity L2 processing interrupted while waiting on queue.");
                interrupted = true;
            }

            if (mViewWeakReference != null) {
                final View view = mViewWeakReference.get();
                if (view != null) {
                    view.postInvalidate();
                }
            }

            // also try to clear out any old committed XB's that are sitting around.
            XB_MANAGER.flushCommittedBlocks();

            if (interrupted) {
                // Somebody is trying to wake us. Bail out of this loop iteration
                // so we can check stopRequested.
                // Note that frame is null, so we're not loosing any data.
                continue;
            }

            if (frame == null) {
                // We must have timed out on an empty queue (or been interrupted).
                // If no new exposure blocks are coming, we can update the last known
                // safe time to commit XB's (with a small fudge factor just incase there's
                // something making its way onto the L2 queue now)
                XB_MANAGER.updateSafeTime(System.currentTimeMillis() - 1000);
                continue;
            }

            // If we made it this far, we have a real data frame ready to go.
            // First update the XB manager's safe time (so it knows which old XB
            // it can commit.
            XB_MANAGER.updateSafeTime(frame.getAcquiredTime());

            ExposureBlock xb = frame.getExposureBlock();

            xb.L2_processed++;

            // Jodi - Removed L2prescale as it was never changed.
            if (L2counter % 1 != 0) {
                // prescaled! Drop the event.
                xb.L2_skip++;
                continue;
            }

            frame.setLocation(CFApplication.getLastKnownLocation());

            // First, build the event from the raw frame.
            ParticleReco.RecoEvent event = PARTICLE_RECO.buildEvent(frame);

            // If we got a bad frame, go straight to stabilization mode.
            if (!PARTICLE_RECO.good_quality && !mFixedThreshold) {
                APPLICATION.setApplicationState(CFApplication.State.STABILIZATION);
                continue;
            }



            // Now do the L2 (pixel-level analysis)
            ArrayList<ParticleReco.RecoPixel> pixels;
            if (APPLICATION.getApplicationState() == CFApplication.State.DATA) {
                pixels = PARTICLE_RECO.buildL2Pixels(frame, xb.L2_thresh);

                // check whether there are too many L2 pixels in this event
                if (pixels.size() > CONFIG.getQualityPixFraction() * PARTICLE_RECO.getTotalPixels()) {
                    // oops! too many pixels in this frame. trigger recalibration.
                    // TODO: consider: should we be recalibrating or just dropping the frame here?
                    APPLICATION.setApplicationState(CFApplication.State.STABILIZATION);
                    continue;
                }
            }
            else {
                // Don't bother with full pixel reco and L2 threshold
                // if we're not actually taking data.
                pixels = PARTICLE_RECO.buildL2PixelsQuick(frame, 0);

                // later add here a check on the fraction of pixels hit

            }

            xb.L2_pass++;

            // Now add them to the event.
            event.pixels = pixels;

            if (APPLICATION.getApplicationState() == CFApplication.State.DATA) {
                // keep track of the running totals for acquired
                // events/pixels over the app lifetime.
                mTotalEvents++;
                mTotalPixels += pixels.size();
            }

            L2counter++;

            // Finally, add the event to the proper exposure block.
            xb.addEvent(event);

            // If we're calibrating, check if we've processed enough
            // frames to decide on the threshold(s) and go back to
            // data-taking mode.
            if (APPLICATION.getApplicationState() == CFApplication.State.CALIBRATION
                    && PARTICLE_RECO.event_count >= CONFIG.getCalibrationSampleFrames()) {
                // mark the time of the last event from the run.
                mCalibrationStop = frame.getAcquiredTime();
                APPLICATION.setApplicationState(CFApplication.State.DATA);
            }
        }
        running = false;
    }

    public int getTotalPixels() {
        return mTotalPixels;
    }

    public int getTotalEvents() {
        return mTotalEvents;
    }

    public long getCalibrationStop() {
        return mCalibrationStop;
    }

    public void setFixedThreshold(boolean fixedThreshold) {
        mFixedThreshold = fixedThreshold;
    }

    public void clearQueue() {
        L2Queue.clear();
    }

    public boolean submitToQueue(@NonNull final RawCameraFrame cameraFrame) {
        return L2Queue.offer(cameraFrame);
    }

    public int getRemainingCapacity() {
        return L2Queue.remainingCapacity();
    }
}
