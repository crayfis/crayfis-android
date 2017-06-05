package edu.uci.crayfis.calibration;

import android.content.Context;
import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float2;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.CFCamera;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private RenderScript mRS;
    private ScriptC_weight mScriptCWeight;
    private Allocation[] mWeights = new Allocation[Camera.getNumberOfCameras()];

    private long start_time;

    private static PreCalibrator sInstance = null;

    public static PreCalibrator getInstance() {
        if(sInstance == null) {
            sInstance = new PreCalibrator();
        }
        return sInstance;
    }

    private PreCalibrator() { }

    public void addFrame(RawCameraFrame frame) {
        frame.getAllocation();
        int cameraId = frame.getCameraId();

        if(mWeights[cameraId] == null
                || mWeights[cameraId].getType().getX() != frame.getWidth()) {

            Type type = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(frame.getWidth())
                    .setY(frame.getHeight())
                    .create();

            mWeights[frame.getCameraId()] = Allocation.createTyped(mRS, type, Allocation.USAGE_SCRIPT);
            mScriptCWeight.set_gWeights(mWeights[cameraId]);

            start_time = System.currentTimeMillis();
        }

        mScriptCWeight.forEach_update(frame.getAllocation());

    }

    public void processPreCalResults(Context context) {

        CFApplication application = (CFApplication)context.getApplicationContext();
        int cameraId = application.getCameraId();

        mScriptCWeight.forEach_findMin(mWeights[cameraId]);
        mScriptCWeight.forEach_normalizeWeights(mWeights[cameraId], mWeights[cameraId]);
        mScriptCWeight.set_gMinSum(256*mScriptCWeight.get_gTotalFrames());

        DataProtos.PreCalibrationResult.Builder b = DataProtos.PreCalibrationResult.newBuilder()
                .setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits())
                .setStartTime(start_time)
                .setEndTime(System.currentTimeMillis());

        addWeightsToBuffer(mWeights[cameraId], b, 1500);

        UploadExposureService.submitPreCalibrationResult(context, b.build());
    }

    /**
     * Downsamples mWeights to a desired size
     *
     * @param maxSampleSize the upper bound for the width x height of the downsampled grid
     * @return an Iterable<> with the downsampled weights
     */
    private void addWeightsToBuffer(Allocation weights, DataProtos.PreCalibrationResult.Builder b, int maxSampleSize) {

        int width = weights.getType().getX();
        int height = weights.getType().getY();
        float[] weightArray = new float[width*height];
        weights.copyTo(weightArray);

        // dimensions of each "block" that make up aspect ratio
        int blockSize = BigInteger.valueOf(height).gcd(BigInteger.valueOf(width)).intValue();

        int sampleStep = (int) Math.sqrt((width*height)/maxSampleSize);

        while(blockSize % sampleStep != 0) {
            sampleStep++;
            if(sampleStep > blockSize) {
                return;
            }
        }

        CFLog.d("Downsample resolution: " + width/sampleStep + "x" + height/sampleStep);

        Random r = new Random();

        for(int iy=0; iy<height; iy+=sampleStep) {
            for(int ix=0; ix<width; ix+=sampleStep) {
                int rx = ix + r.nextInt(sampleStep);
                int ry = iy + r.nextInt(sampleStep);

                b.addWeights(weightArray[rx + width*ry]);
            }
        }

        b.setSampleResX(width/sampleStep)
                .setSampleResY(height/sampleStep);
    }


    public ScriptC_weight getScriptCWeight(RenderScript rs) {
        if(mScriptCWeight == null) {
            mRS = rs;
            mScriptCWeight = new ScriptC_weight(rs);
            mScriptCWeight.set_gTotalFrames(CFConfig.getInstance().getPreCalibrationSampleFrames());
        }
        return mScriptCWeight;
    }

    public void clear(int cameraId) {
        mWeights[cameraId] = null;
    }

    public boolean dueForPreCalibration(int cameraId) {
        return mWeights[cameraId] == null
                || CFApplication.getCameraSize().width != mWeights[cameraId].getType().getX();
    }

}
