package edu.uci.crayfis.calibration;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import java.util.Arrays;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

import static edu.uci.crayfis.camera.RawCameraFrame.BORDER;

/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private RenderScript mRS;
    private ScriptC_weight mScriptCWeight;
    private Allocation mWeights;

    public int preCalCount = 0;
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
        preCalCount++;
        frame.getAllocation();

        if(mWeights == null || mWeights.getType().getX() != frame.getWidth()) {

            Type type = new Type.Builder(mRS, Element.F32(mRS))
                    .setX(frame.getWidth())
                    .setY(frame.getHeight())
                    .create();

            mWeights = Allocation.createTyped(mRS, type, Allocation.USAGE_SCRIPT);

            // define aweights
            float[] maskArray = new float[(frame.getWidth()-2*BORDER)*(frame.getHeight()-2*BORDER)];

            // start with equal weights
            Arrays.fill(maskArray, 1f);

            mWeights.copy2DRangeFrom(BORDER, BORDER, type.getX()-2*BORDER, type.getY()-2*BORDER, maskArray);

            mScriptCWeight.set_gWeights(mWeights);
            mScriptCWeight.set_gScript(mScriptCWeight);
        }

        mScriptCWeight.invoke_update_weights(frame.getAllocation());
    }


    public ScriptC_weight getScriptCWeight(RenderScript rs) {
        if(mScriptCWeight == null) {
            mRS = rs;
            mScriptCWeight = new ScriptC_weight(rs);
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