package edu.uci.crayfis.calibration;

/**
 * Created by Jeff on 4/21/2017.
 */

class FrameHistogram extends FrameHistory<Integer> {

    private Histogram h = new Histogram(256);

    FrameHistogram(int n) {
        super(n);
    }

    @Override
    public void addValue(Integer value) {
        synchronized(values) {
            if (values.size() >= n_frames) {
                h.remove(values.poll());
            }

            values.add(value);
            h.fill(value);
        }
    }

    public Histogram getHistogram() { return h; }

}
