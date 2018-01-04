package io.crayfis.android.calibration;

import android.support.annotation.NonNull;

import java.util.Iterator;

import io.crayfis.android.util.CFLog;

/**
 * Created by cshimmin on 11/18/14.
 */
public class Histogram implements Iterable<Long> {
    private long[] values;
    private double[] errors;
    private boolean mean_valid = false;
    private boolean variance_valid = false;
    private double mean = 0;
    private double variance = 0;
    private double integral = 0;
    private long entries = 0;
    private boolean doErrors = false;
    private final int nbins;

    private long underflow;
    private double underflowError;
    private long overflow;
    private double overflowError;


    // Iterator over the values of each bin. Does not include overflow/underflow bins.
    private class HistogramIterator implements Iterator<Long> {
        private int pos = 0;
        public Long next() { pos += 1; return values[pos-1]; }
        public boolean hasNext() { return (pos < values.length-1); }
        public void remove() { throw new UnsupportedOperationException(); }
    }

    public Histogram(int nbins) {
        this(nbins, false);
    }

    public Histogram(int nbins, boolean doErrors) {
        if (nbins <= 0) {
            throw new RuntimeException("Hey dumbo a histogram should have at least one bin.");
        }

        this.nbins = nbins;
        this.doErrors = doErrors;

        // make two additional bins for under/overflow
        values = new long[nbins];

        if (doErrors) {
            errors = new double[nbins];
        }
    }

    @Override
    public String toString()
    {
        String res="";
        double n_total = getIntegral();
        int nprint = 0;
        for (int i=0;i<values.length && nprint<10;i++) {
            double rate = getIntegral(i, 256) / (1.0*n_total);
            if (values[i] == 0) { continue; }
            nprint++;
            res += "[ bin " + i + " = " + values[i] + " eff = " + String.format("%.3f", rate) + "] \n";
        }
        return res;
    }

    public void clear() {
        for (int i = 0; i < values.length; ++i) {
            values[i] = 0;
        }
        integral = 0;
        mean_valid = false;
        variance_valid = false;
        entries = 0;
    }

    // fill a new entry in the histogram, for the given value of the histogram variable.
    // optionally, a floating-point weight may be added.
    public void fill(int val) {
        fill(val, 1);
    }
    public void remove(int val) { fill(val, -1); }
    public void fill(int x, int weight) {
        // keep track of the total number of raw entries filled
        entries += Math.signum(weight);

        // underflow/overflow entries get added to the end of the normal array
        // they do not contribute to the integral or other stats, and are not
        // included for iteration
        if (x < 0) {
            // underflow
            underflow += weight;
            if (doErrors) {
                double lastval = underflowError;
                underflowError = Math.sqrt(weight * weight + lastval * lastval);
            }
        }
        else if (x >= nbins) {
            // overflow
            overflow += weight;
            if (doErrors) {
                double lastval = overflowError;
                overflowError = Math.sqrt(weight * weight + lastval * lastval);
            }
        }
        else {
            // fill the selected bin and update the statistics.
            values[x] += weight;
            integral += weight;
            invalidateStats();

            if (doErrors) {
                double lastval = errors[x];
                errors[x] = Math.sqrt(weight*weight + lastval*lastval);
            }
        }
    }

    public void fill(int[] values) {
        for(int i=0; i<values.length; i++) {
            if(values[i] > 0) {
                fill(i, values[i]);
            }
        }

    }

    public void merge(Histogram hist) {
        entries += hist.getEntries();
        integral += hist.getIntegral();

        underflow += hist.getUnderflow();
        overflow += hist.getOverflow();
        for(int i=0; i<hist.getValues().length; i++) {
            values[i] += hist.getBinValue(i);
        }

        if(doErrors) {
            underflowError = Math.sqrt(underflowError*underflowError + hist.getUnderflowError()*hist.getUnderflowError());
            overflowError = Math.sqrt(overflowError*overflowError + hist.getOverflowError()*hist.getOverflowError());
        }
    }

    // mark the statistics as invalid, forcing recomputation
    // the next time any of them are called.
    private void invalidateStats() {
        mean_valid = false;
        variance_valid = false;
    }

    // calculate the mean value of the histogram variable
    public double getMean() {
        // if the mean has not been invalidated since the last
        // time we calculated it, just return the previous value.
        if (!mean_valid) {

            int sum = 0;
            for (int i = 0; i < nbins; ++i) {
                sum += values[i];
            }
            mean = ((double) sum) / integral;
            mean_valid = true;
        }
        return mean;
    }

    // calculate the variance of the histogram variable
    public double getVariance() {
        // if the variance has not been invalidated since the last
        // time we calculated it, just return the previous value.
        if (!variance_valid) {

            mean = getMean();
            int sum = 0;
            for (int i = 0; i < nbins; ++i) {
                double val = values[i];
                sum += (val - mean) * (val - mean);
            }
            variance = ((double) sum) / integral;
            variance_valid = true;
        }
        return variance;
    }

    // return the integral (sum of weights). does not include overflow/underflow bins.
    public double getIntegral() { return integral; }

    // return the integral (sum of weights) over the specified (inclusive) bin range.
    public double getIntegral(int a, int b) {
        return getIntegral(a, b, false);
    }
    public double getIntegral(int a, int b, boolean overflow) {
        if (b < a) {
            return 0;
        }

        double integral = 0;

        if (a<0) {
            a = 0;
            if (overflow) integral += getUnderflow();
        }
        if (b>=nbins) {
            b = nbins-1;
            if (overflow) integral += getOverflow();
        }

        for (int i = a; i <= b; ++i) {
            if (values[i] > 0)
                integral += values[i];
        }

        return integral;
    }

    // return the value of overflow/underflow bins.
    public long getUnderflow() {
        return underflow;
    }
    public long getOverflow() {
        return overflow;
    }
    public double getUnderflowError() {
        if (doErrors) {
            return underflowError;
        } else {
            return 0;
        }
    }
    public double getOverflowError() {
        if (doErrors) {
            return overflowError;
        } else {
            return 0;
        }
    }

    // get the value of a specific bin. returns 0 for any bins outside the range.
    public long getBinValue(int i) {
        if (i >= 0 && i < nbins) {
            return values[i];
        } else {
            return 0;
        }
    }

    // get the error of a specific bin. returns 0 if this histogram is not tracking errors.
    public double getBinError(int i) {
        if (doErrors && i>=0 && i < nbins) {
            return errors[i];
        } else {
            return 0;
        }
    }

    // get the raw number of entries filled.
    public long getEntries() { return entries; }

    // get an iterator over the bin contents. iterator does not include overflow/underflow bins.
    @NonNull
    public Iterator<Long> iterator() { return new HistogramIterator(); }

    // get a copy of the whole array of bin values.
    public long[] getValues() { return values.clone(); }
}