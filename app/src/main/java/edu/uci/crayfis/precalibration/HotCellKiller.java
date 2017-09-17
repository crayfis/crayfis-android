package edu.uci.crayfis.precalibration;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgproc.Imgproc;

import java.util.HashSet;
import java.util.Set;

import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.ScriptC_findSecond;
import edu.uci.crayfis.camera.CFCamera;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 6/6/2017.
 */

class HotCellKiller extends PrecalComponent {

    private final Set<Integer> HOTCELL_COORDS;

    private final CFConfig CONFIG = CFConfig.getInstance();
    private final CFCamera CAMERA = CFCamera.getInstance();

    private ScriptC_findSecond mScriptCFindSecond;
    private Allocation aMax;
    private Allocation aSecond;

    HotCellKiller(RenderScript rs, DataProtos.PreCalibrationResult.Builder b) {
        super(rs, b);
        CFLog.i("HotCellKiller created");
        mScriptCFindSecond = new ScriptC_findSecond(RS);
        sampleFrames = CONFIG.getHotcellSampleFrames();

        Type type = new Type.Builder(RS, Element.U8(RS))
                .setX(CAMERA.getResX())
                .setY(CAMERA.getResY())
                .create();
        aMax = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        aSecond = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
        mScriptCFindSecond.set_aMax(aMax);
        mScriptCFindSecond.set_aSecond(aSecond);
        HOTCELL_COORDS = new HashSet<>();
    }

    /**
     * Keeps allocations of running largest and second-largest values for each pixel
     *
     * @param frame RawCameraFrame
     * @return true if we have reached the requisite number of frames, false otherwise
     */
    @Override
    boolean addFrame(RawCameraFrame frame) {
        mScriptCFindSecond.forEach_order(frame.getWeightedAllocation());
        return super.addFrame(frame);
    }

    /**
     *
     */
    @Override
    void process() {
        super.process();
        int cameraId = CAMERA.getCameraId();

        // set up Allocations for histogram
        ScriptIntrinsicHistogram histogram = ScriptIntrinsicHistogram.create(RS, Element.U8(RS));
        Allocation aout = Allocation.createSized(RS, Element.U32(RS), 256, Allocation.USAGE_SCRIPT);
        histogram.setOutput(aout);
        histogram.forEach(aSecond);

        int[] secondHist = new int[256];
        aout.copyTo(secondHist);

        for(int i=0; i<secondHist.length; i++) {
            if (secondHist[i] != 0) {
                CFLog.d("hist[" + i + "] = " + secondHist[i]);
            }
        }

        // find minimum value in aSecond considered as "hot"
        int area = aMax.getType().getX() * aMax.getType().getY();
        int target = (int) (CONFIG.getHotcellThresh() * area);
        int pixRemaining = area;

        int cutoff=0;
        while(pixRemaining > target) {
            pixRemaining -= secondHist[cutoff];
            cutoff++;
        }
        CFLog.d("cutoff = " + cutoff);

        // copy to OpenCV to locate hot pixels
        byte[] maxArray = new byte[area];
        byte[] secondArray = new byte[area];
        aMax.copyTo(maxArray);
        aSecond.copyTo(secondArray);
        MatOfByte secondMat = new MatOfByte(secondArray);

        Mat threshMat = new Mat();
        Mat hotcellCoordsMat = new Mat();

        Imgproc.threshold(secondMat, threshMat, cutoff-1, 0, Imgproc.THRESH_TOZERO);
        secondMat.release();
        Core.findNonZero(threshMat, hotcellCoordsMat);
        threshMat.release();

        for(int i=0; i<hotcellCoordsMat.total(); i++) {
            double[] hotcellCoords = hotcellCoordsMat.get(i,0);
            int pos = (int) hotcellCoords[1];
            int width = aSecond.getType().getX();
            int x = pos % width;
            int y = pos / width;

            HOTCELL_COORDS.add(pos);

            // check pixels adjacent to hotcells, and see if max values are above the cutoff
            for(int dx=x-1; dx<=x+1; dx++) {
                for(int dy=y-1; dy<=y+1; dy++) {
                    try {
                        int adjMax = maxArray[dx + width * dy] & 0xFF;
                        if (adjMax >= cutoff) {
                            HOTCELL_COORDS.add(dx + width * dy);
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // don't crash if we're on the border
                    }
                }
            }
        }

        // store in CFConfig and protobuf file
        CFLog.d("Total hotcells found: " + HOTCELL_COORDS.size());
        CONFIG.setHotcells(cameraId, HOTCELL_COORDS);

        for(Integer pos: HOTCELL_COORDS) {
            PRECAL_BUILDER.addHotcell(pos);
        }

        int maxNonZero = 255;
        while (secondHist[maxNonZero] == 0) {
            maxNonZero--;
        }
        for (int i = 0; i <= maxNonZero; i++) {
            PRECAL_BUILDER.addSecondHist(secondHist[i]);
        }
    }
}
