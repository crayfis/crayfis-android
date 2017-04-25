package edu.uci.crayfis.calibration;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import java.util.Arrays;

import edu.uci.crayfis.ScriptC_weight;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

import static edu.uci.crayfis.camera.RawCameraFrame.BORDER;

/**
 * Created by Jeff on 4/24/2017.
 */

public class PreCalibrator {

    private ScriptC_weight mScriptCWeight;
    private Allocation weights;

    public static int preCalCount = 0;
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
        mScriptCWeight.forEach_update_weights(frame.getAllocation(), null);
    }

    public ScriptC_weight getScriptCWeight(RenderScript rs, int x, int y) {
        if(mScriptCWeight == null) {
            mScriptCWeight = new ScriptC_weight(rs);
        }
        if(weights == null || weights.getType().getX() != x) {

            Type type = new Type.Builder(rs, Element.F32(rs))
                    .setX(x)
                    .setY(y)
                    .create();

            weights = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT);

            // define aweights
            float[] maskArray = new float[(x-2*BORDER)*(y-2*BORDER)];

            // for now, just use equal weights
            Arrays.fill(maskArray, 1f);

            weights.copy2DRangeFrom(BORDER, BORDER, type.getX()-2*BORDER, type.getY()-2*BORDER, maskArray);

            mScriptCWeight.set_weights(weights);
        }
        return mScriptCWeight;
    }

}
