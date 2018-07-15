package io.crayfis.android.camera;

import android.hardware.Camera;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Base64;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.server.PreCalibrationService;
import io.crayfis.android.util.FrameHistory;
import io.crayfis.android.exposure.ExposureBlockManager;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 8/31/2017.
 */

public abstract class CFCamera {

    CFApplication mApplication;
    final CFConfig CONFIG;
    RenderScript mRS;

    public final static int INTER = Imgproc.INTER_CUBIC;

    // thread for Camera callbacks
    protected Handler mCameraHandler;
    protected final HandlerThread mCameraThread = new HandlerThread("CFCamera2");;

    // thread for posting jobs handling frames
    protected Handler mFrameHandler;
    protected final HandlerThread mFrameThread = new HandlerThread("RawCamera2Frame");

    private CFSensor mCFSensor;
    private CFLocation mCFLocation;

    int mCameraId = -1;
    int mResX;
    int mResY;

    private PreCalibrationService.PreCalibrationConfig mPrecalConfig;

    final FrameHistory<Long> mTimestampHistory = new FrameHistory<>(100);

    private static CFCamera sInstance;

    final RawCameraFrame.Builder RCF_BUILDER = new RawCameraFrame.Builder();

    CFCamera() {

        CONFIG = CFConfig.getInstance();
    }

    /**
     * Gets the current instance CFCamera
     *
     * @return CFCamera
     */
    public static synchronized CFCamera getInstance() {

        if(sInstance == null) {

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                sInstance = new CFCameraDeprecated();
            } else {
                sInstance = new CFCamera2();
            }
        }
        return sInstance;
    }

    /**
     * Instates camera, sensors, and location services
     *
     * @param app the application
     */
    public synchronized void register(CFApplication app) {

        if(mApplication != null) return; // already registered

        mApplication = app;
        mRS = mApplication.getRenderScript();

        mCFSensor = new CFSensor(app, RCF_BUILDER);
        mCFLocation = new CFLocation(app, RCF_BUILDER);

        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mFrameThread.start();
        mFrameHandler = new Handler(mFrameThread.getLooper());


        changeCamera();
    }

    /**
     * Unregisters cameras, sensors, and location services
     */
    public synchronized void unregister() {

        if(!mCameraThread.isAlive()) return; // already unregistered
        mCameraId = -1;

        changeCameraFrom(mCameraId, true);

        // quit the existing camera thread
        try {
            mCameraThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mFrameThread.quitSafely();
        try {
            mFrameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mCFSensor.unregister();
        mCFLocation.unregister();
        sInstance = null;
    }

    abstract void configure();
    public abstract void changeDataRate(boolean increase);

    /**
     * Tear down camera and, if appropriate, start a new stream.
     */
    public void changeCamera() {
        changeCameraFrom(mCameraId);
    }

    public void changeCameraFrom(final int currentId) {
        changeCameraFrom(currentId, false);
    }

    /**
     * The same as changeCamera(), except marks the ID of a bad frame to avoid changing camera
     * multiple times unnecessarily
     *
     * @param currentId The cameraId of the bad frame
     * @param quit Whether we quit the thread after changing the camera
     */
    private synchronized void changeCameraFrom(final int currentId, final boolean quit) {

        if (currentId != mCameraId || !mCameraThread.isAlive()) return;

        // clear out old stats
        mPrecalConfig = null;
        RCF_BUILDER.setWeights(null);
        synchronized (mTimestampHistory) {
            mTimestampHistory.clear();
        }

        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {

                // find next camera based on DAQ state
                int nextId = -1;
                CFApplication.State state = mApplication.getApplicationState();
                switch (state) {
                    case INIT:
                        nextId = 0;
                        break;
                    case SURVEY:
                        // switch cameras and try again
                        nextId = currentId + 1;
                        if (nextId >= Camera.getNumberOfCameras()) {
                            nextId = -1;
                        } else {
                            // need a new SURVEY XB for next camera
                            ExposureBlockManager.getInstance().abortExposureBlock();
                        }
                        break;
                    case PRECALIBRATION:
                    case CALIBRATION:
                    case DATA:
                    case IDLE:
                    case FINISHED:
                        // take a break for a while
                        nextId = -1;
                }

                mCameraId = nextId;

                if (nextId == -1 && state != CFApplication.State.IDLE && state != CFApplication.State.FINISHED) {
                    mApplication.startSurveyTimer();
                }

                CFLog.i("cameraId:" + currentId + " -> " + nextId);

                configure();

                if (state == CFApplication.State.INIT) {
                    mApplication.setApplicationState(CFApplication.State.SURVEY);
                }

                // if we are unregistering the camera
                if(quit && mCameraId == -1) {
                    mCameraThread.quitSafely();
                }
            }
        });

    }

    /**
     * Calculates and returns the average FPS of the last 100 frames produced
     * @return double
     */
    public double getFPS() {
        synchronized(mTimestampHistory) {
            int nframes = mTimestampHistory.size();
            if (nframes>0) {
                return ((double) nframes) / (System.nanoTime() - mTimestampHistory.getOldest()) * 1000000000L;
            }
        }

        return 0.0;
    }

    public int getCameraId() {
        return mCameraId;
    }

    /**
     * Obtain a detailed string of camera settings to be included in the RunConfig file
     *
     * @return String
     */
    public abstract String getParams();

    /**
     * Obtain a String to be displayed in the Developer panel.
     *
     * @return String
     */
    public String getStatus() {
        String devtxt = "Camera ID: " + mCameraId + ", FPS = " + String.format("%.02f", getFPS()) + "\n";
        devtxt += mCFSensor.getStatus() + mCFLocation.getStatus();
        return devtxt;
    }

    public void setExposureBlock(ExposureBlock xb) {
        RCF_BUILDER.setExposureBlock(xb);
    }

    public PreCalibrationService.PreCalibrationConfig getPrecalConfig() {
        return mPrecalConfig;
    }

    public void setPrecalConfig(PreCalibrationService.PreCalibrationConfig config) {

        mPrecalConfig = config;

        // configure weights in RS as well
        byte[] bytes = Base64.decode(config.getB64Weights(), Base64.DEFAULT);

        MatOfByte compressedMat = new MatOfByte(bytes);
        Mat downsampleMat = Imgcodecs.imdecode(compressedMat, 0);
        compressedMat.release();

        Mat resampledMat2D = new Mat();
        Imgproc.resize(downsampleMat, resampledMat2D, new Size(mResX, mResY), 0, 0, INTER);
        downsampleMat.release();

        Mat resampledMat = resampledMat2D.reshape(0, resampledMat2D.cols() * resampledMat2D.rows());
        MatOfByte resampledByte = new MatOfByte(resampledMat);
        resampledMat2D.release();
        resampledMat.release();

        byte[] resampledArray = resampledByte.toArray();
        resampledByte.release();

        // kill hotcells in resampled frame;
        if(config.getHotcells() != null) {
            for (Integer pos : config.getHotcells()) {
                resampledArray[pos] = (byte) 0;
            }
        }

        RenderScript RS = mApplication.getRenderScript();

        Type weightType = new Type.Builder(RS, Element.U8(RS))
                .setX(mResX)
                .setY(mResY)
                .create();
        Allocation weights = Allocation.createTyped(RS, weightType, Allocation.USAGE_SCRIPT);
        weights.copyFrom(resampledArray);

        resampledMat.release();
        resampledByte.release();

        ScriptC_weight scriptCWeight = new ScriptC_weight(RS);

        scriptCWeight.set_gWeights(weights);
        RCF_BUILDER.setWeights(scriptCWeight);
    }

    public int getResX() {
        return mResX;
    }

    public int getResY() {
        return mResY;
    }

    public boolean isFlat() {
        return !(mCFSensor == null) && mCFSensor.isFlat();
    }

    public Location getLastKnownLocation() {
        if(mCFLocation == null) return null;
        return mCFLocation.currentLocation;
    }

    public boolean isUpdatingLocation() {
        return !(mCFLocation == null) && mCFLocation.isReceivingUpdates();
    }
}
