package edu.uci.crayfis.calibration;

import java.util.ArrayDeque;
import java.util.Queue;

public class FrameHistory<E extends Number> {
    final private Queue<E> values;
    private int n_frames;
    private Integer max_val = -1;

    public FrameHistory(int n_frames) {
        this.n_frames = n_frames;
        values = new ArrayDeque<E>(n_frames);
    }

    public void clear() {
        values.clear();
    }

    public int size() {
        return values.size();
    }

    public void resize(int n) {
        if (n >= n_frames) return;
        synchronized (values) {
            while (values.size() >= n_frames) {
                values.poll();
            }
        }
    }

    public void add_value(E value) {
        synchronized(values) {
            if (values.size() >= n_frames) {
                values.poll();
            }

            values.add(value);
        }
    }

    public Histogram getHistogram(int nbins) {
        Histogram h = new Histogram(nbins);

        synchronized (values) {
            for (E i : values) {
                h.fill(i.intValue());
            }
        }

        return h;
    }

    public E[] toArray(E in[]) {
        return (E[]) values.toArray(in);
    }

    public E getOldest() {
        return values.peek();
    }
}
