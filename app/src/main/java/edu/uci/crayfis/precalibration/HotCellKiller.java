package edu.uci.crayfis.precalibration;

import android.hardware.Camera;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 6/6/2017.
 */

class HotCellKiller {

    private final List<ArrayList<Hotcell>> HOTCELL_COORDS = new ArrayList<>(Camera.getNumberOfCameras());

    HotCellKiller() {
        for(int i=0; i<Camera.getNumberOfCameras(); i++) {
            HOTCELL_COORDS.add(new ArrayList<Hotcell>(10));

        }
    }

    boolean addFrame(RawCameraFrame frame) {
        return false;
    }

    void clearHotcells(int cameraId) {
        synchronized (HOTCELL_COORDS) {
            HOTCELL_COORDS.get(cameraId).clear();
        }
    }

    ArrayList<Hotcell> getHotcellCoords(int cameraId) {
        return HOTCELL_COORDS.get(cameraId);
    }

    class Hotcell {

        int x;
        int y;

        Hotcell(int x, int y) {
            this.x = x;
            this.y = y;
        }

    }

}
