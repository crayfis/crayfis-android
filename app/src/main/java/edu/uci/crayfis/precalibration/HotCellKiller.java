package edu.uci.crayfis.precalibration;

import android.content.Context;
import android.hardware.Camera;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.renderscript.Type;
import android.support.annotation.NonNull;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.ScriptC_findSecond;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 6/6/2017.
 */

class HotCellKiller {

    final List<Set<Integer>> HOTCELL_COORDS = new ArrayList<>(Camera.getNumberOfCameras());
    int[] secondHist;

    private final CFConfig CONFIG = CFConfig.getInstance();

    private final RenderScript RS;
    private final ScriptC_findSecond SCRIPT_C_FIND_SECOND;
    private Allocation aMax;
    private Allocation aSecond;

    private Integer mCount = 0;

    HotCellKiller(RenderScript rs) {
        for(int i=0; i<Camera.getNumberOfCameras(); i++) {
            HOTCELL_COORDS.add(new HashSet<Integer>(10));
        }
        RS = rs;
        SCRIPT_C_FIND_SECOND = new ScriptC_findSecond(RS);
    }

    boolean addFrame(RawCameraFrame frame) {

        mCount++;

        if(aMax == null) {

            Type type = new Type.Builder(RS, Element.U8(RS))
                    .setX(frame.getWidth())
                    .setY(frame.getHeight())
                    .create();
            aMax = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
            aSecond = Allocation.createTyped(RS, type, Allocation.USAGE_SCRIPT);
            SCRIPT_C_FIND_SECOND.set_aMax(aMax);
            SCRIPT_C_FIND_SECOND.set_aSecond(aSecond);
        }

        SCRIPT_C_FIND_SECOND.forEach_order(frame.getAllocation());

        synchronized (mCount) {
            if(mCount >= CONFIG.getHotcellSampleFrames()) {
                mCount = 0;
                findHotcells(frame.getCameraId());
                return false;
            }
        }
        return true;
    }

    private void findHotcells(int cameraId) {

        ScriptIntrinsicHistogram histogram = ScriptIntrinsicHistogram.create(RS, Element.U8(RS));
        Allocation aout = Allocation.createSized(RS, Element.U32(RS), 256, Allocation.USAGE_SCRIPT);
        histogram.setOutput(aout);
        histogram.forEach(aSecond);

        secondHist = new int[256];
        aout.copyTo(secondHist);

        for(int i=0; i<secondHist.length; i++) {
            if(secondHist[i] != 0)
            CFLog.d("hist[" + i + "] = " + secondHist[i]);
        }

        int area = aMax.getType().getX() * aMax.getType().getY();
        int target = (int) (CONFIG.getHotcellThresh() * area);
        int pixRemaining = area;

        int cutoff=0; // minimum value in aSecond considered as "hot"
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

            HOTCELL_COORDS.get(cameraId).add(pos);

            for(int dx=x-1; dx<=x+1; dx++) {
                for(int dy=y-1; dy<=y+1; dy++) {
                    try {
                        int adjMax = maxArray[dx + width * dy] & 0xFF;
                        if (adjMax >= cutoff) {
                            HOTCELL_COORDS.get(cameraId).add(dx + width * dy);
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // don't crash if we're on the border
                    }
                }
            }
        }

        CFLog.d("Total hotcells found: " + HOTCELL_COORDS.get(cameraId).size());

    }

    void clearHotcells(int cameraId) {
        synchronized (HOTCELL_COORDS) {
            HOTCELL_COORDS.get(cameraId).clear();
        }
        mCount = 0;
        aMax = null;
        aSecond = null;
    }
}
