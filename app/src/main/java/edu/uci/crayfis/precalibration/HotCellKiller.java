package edu.uci.crayfis.precalibration;

import java.util.ArrayList;

import edu.uci.crayfis.camera.RawCameraFrame;

/**
 * Created by Jeff on 6/6/2017.
 */

class HotCellKiller {

    private final ArrayList<Hotcell> HOTCELL_COORDS = new ArrayList<>(10);

    boolean addFrame(RawCameraFrame frame) {

        return false;
    }

    void clearHotcells() {
        synchronized (HOTCELL_COORDS) {
            HOTCELL_COORDS.clear();
        }
    }

    ArrayList<Hotcell> getHotcellCoords() {
        return HOTCELL_COORDS;
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
