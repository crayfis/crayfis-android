package edu.uci.crayfis.calibration;

import android.annotation.TargetApi;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.Type;

import java.util.Arrays;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;


/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private RenderScript mRS;
    private ScriptC_weight mScriptCWeight;
    private Allocation mWeights;

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
        }

        //Script.LaunchOptions lo = new Script.LaunchOptions()
        //        .setX(BORDER, frame.getWidth()-BORDER)
        //        .setY(BORDER, frame.getHeight()-BORDER);

        mScriptCWeight.forEach_update(frame.getAllocation());

    }

    public void processPreCalResults() {
        mScriptCWeight.forEach_findMin(mWeights);
        mScriptCWeight.forEach_normalizeWeights(mWeights, mWeights);
        mScriptCWeight.set_gMinSum(256*mScriptCWeight.get_gTotalFrames());
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
