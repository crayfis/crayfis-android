package edu.uci.crayfis.calibration;

import android.content.Context;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.server.UploadExposureService;
import edu.uci.crayfis.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private RenderScript mRS;
    private ScriptC_weight mScriptCWeight;
    private Allocation mWeights;

    private long start_time;

    private static PreCalibrator sInstance = null;

    public static PreCalibrator getInstance() {
        if(sInstance == null) {
            sInstance = new PreCalibrator();
        }
        return sInstance;
    }

    private PreCalibrator() {

    }

    public void addFrame(RawCameraFrame frame) {
        frame.getAllocation();

        if(mWeights == null || mWeights.getType().getX() != frame.getWidth()) {

            Type type = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(frame.getWidth())
                    .setY(frame.getHeight())
                    .create();

            mWeights = Allocation.createTyped(mRS, type, Allocation.USAGE_SCRIPT);
            mScriptCWeight.set_gWeights(mWeights);

            start_time = System.currentTimeMillis();
        }

        //Script.LaunchOptions lo = new Script.LaunchOptions()
        //        .setX(BORDER, frame.getWidth()-BORDER)
        //        .setY(BORDER, frame.getHeight()-BORDER);

        mScriptCWeight.forEach_update(frame.getAllocation());

    }

    public void processPreCalResults(Context context) {
        mScriptCWeight.forEach_findMin(mWeights);
        mScriptCWeight.forEach_normalizeWeights(mWeights, mWeights);
        mScriptCWeight.set_gMinSum(256*mScriptCWeight.get_gTotalFrames());

        DataProtos.PreCalibrationResult.Builder b = DataProtos.PreCalibrationResult.newBuilder();
        CFApplication application = (CFApplication)context.getApplicationContext();
        b.setRunId(application.getBuildInformation().getRunId().getLeastSignificantBits());
        b.setStartTime(start_time);
        b.setEndTime(System.currentTimeMillis());
        UploadExposureService.submitPreCalibrationResult(context, b.build());
    }


    public ScriptC_weight getScriptCWeight(RenderScript rs) {
        if(mScriptCWeight == null) {
            mRS = rs;
            mScriptCWeight = new ScriptC_weight(rs);
            mScriptCWeight.set_gTotalFrames(CFConfig.getInstance().getPreCalibrationSampleFrames());
        }
        return mScriptCWeight;
    }

    public void clear() {
        mWeights = null;
    }

    public boolean dueForPreCalibration() {
        return mWeights == null || CFApplication.getCameraSize().width != mWeights.getType().getX();
    }

}
