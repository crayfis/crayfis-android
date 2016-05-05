package edu.uci.crayfis;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.AsyncTask;

import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by cshimmin on 5/4/16.
 */
public class L1Processor {

    private CFApplication mApplication = null;
    private boolean mRecycle = false;

    private L1Calibrator mL1Cal = null;
    private int mL1Count = 0;
    private int mCalibrationCount = 0;
    private int mStabilizationCount = 0;

    private int mBufferBalance = 0;

    private L2Processor mL2Processor = null;

    private final CFConfig CONFIG = CFConfig.getInstance();

    public L1Processor(CFApplication application, boolean recycle) {
        mApplication = application;
        mRecycle = recycle;

        mL1Cal = L1Calibrator.getInstance();
    }

    public void setL2Processor(L2Processor l2) {
        mL2Processor = l2;
    }

    private class L1Task implements Runnable {
        private RawCameraFrame mFrame = null;
        private Camera mCamera = null;

        private L1Task(RawCameraFrame frame, Camera camera) {
            mFrame = frame;
            mCamera = camera;
        }

        private byte[] createPreviewBuffer() {
            Camera.Parameters params = mFrame.getParams();
            Camera.Size sz = params.getPreviewSize();
            int imgsize = sz.height*sz.width;
            int formatsize = ImageFormat.getBitsPerPixel(params.getPreviewFormat());
            int bsize = imgsize * formatsize / 8;
            CFLog.i("Creating new preview buffer; imgsize = " + imgsize + " formatsize = " + formatsize + " bsize = " + bsize);
            return new byte[bsize+1];
        }

        @Override
        public void run() {
            int frameNumber = ++mL1Count;

            // show the frame to the L1 calibrator
            mL1Cal.AddFrame(mFrame);

            final CFApplication application = mApplication;
            if (application.getApplicationState() == CFApplication.State.CALIBRATION) {
                // if we are in (L1) calibration mode, there's no need to do anything else with this
                // frame; the L1 calibrator already saw it. Just check to see if we're done calibrating.
                int count = ++mCalibrationCount;

                if (count > CONFIG.getCalibrationSampleFrames()) {
                    application.setApplicationState(CFApplication.State.DATA);
                }

                if (mRecycle) {
                    mBufferBalance--;
                    mCamera.addCallbackBuffer(mFrame.getBytes());
                }
                return;
            }
            if (application.getApplicationState() == CFApplication.State.STABILIZATION) {
                // If we're in stabilization mode, just drop frames until we've skipped enough
                int count = ++mStabilizationCount;
                if (count > CONFIG.getStabilizationSampleFrames()) {
                    application.setApplicationState(CFApplication.State.CALIBRATION);
                }
                if (mRecycle) {
                    mBufferBalance--;
                    mCamera.addCallbackBuffer(mFrame.getBytes());
                }
                return;
            }
            if (application.getApplicationState() == CFApplication.State.IDLE) {
                // Not sure why we're still acquiring frames in IDLE mode...
                CFLog.w("DAQActivity Frames still being recieved in IDLE mode");
                if (mRecycle) {
                    mBufferBalance--;
                    mCamera.addCallbackBuffer(mFrame.getBytes());
                }
                return;
            }

            if (mL2Processor.getRemainingCapacity() > 0) {
                // check if we pass the L1 threshold
                boolean pass = false;

                ExposureBlock xb = mFrame.getExposureBlock();

                int max = mFrame.getPixMax();
                if (max > xb.L1_thresh) {
                    // NB: we compare to the XB's L1_thresh, as the global L1 thresh may
                    // have changed.
                    pass = true;
                }

                if (pass) {
                    xb.L1_pass++;

                    // add a new buffer to the queue to make up for this one which
                    // will not return
                    if (mRecycle) {
                        mBufferBalance--;
                        mCamera.addCallbackBuffer(createPreviewBuffer());
                    }

                    // this frame has passed the L1 threshold, put it on the
                    // L2 processing queue.
                    boolean queue_accept = mL2Processor.submitToQueue(mFrame);

                    if (!queue_accept) {
                        // oops! the queue is full... this frame will be dropped.
                        CFLog.e("DAQActivity Could not add frame to L2 Queue!");
                        //L2busy++;
                    }
                } else {
                    // didn't pass. recycle the buffer.
                    xb.L1_skip++;
                    if (mRecycle) {
                        mBufferBalance--;
                        mCamera.addCallbackBuffer(mFrame.getBytes());
                    }
                }
            } else {
                // oops.
                CFLog.e("L2 queue is busy!");
                if (mRecycle) {
                    mBufferBalance--;
                    mCamera.addCallbackBuffer(mFrame.getBytes());
                }
            }
        }
    }

    public void submitFrame(RawCameraFrame frame, Camera camera) {
        mBufferBalance++;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new L1Task(frame, camera));
    }

}
