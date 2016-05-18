package edu.uci.crayfis.calibration;

import java.util.Iterator;

/**
 * Created by cshimmin on 11/18/14.
 */
public class Histogram implements Iterable<Integer> {
    private int[] values;
    private double[] errors;
    private boolean mean_valid = false;
    private boolean variance_valid = false;
    private double mean = 0;
    private double variance = 0;
    private double integral = 0;
    private int entries = 0;
    private int max_bin = 0;
    private boolean doErrors = false;
    public final int nbins;


    // Iterator over the values of each bin. Does not include overflow/underflow bins.
    public class HistogramIterator implements Iterator<Integer> {
        private int pos = 0;
        public Integer next() { pos += 1; return values[pos-1]; }
        public boolean hasNext() { return (pos < values.length); }
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
        values = new int[nbins+2];

        if (doErrors) {
            errors = new double[nbins+2];
        }
    }

    @Override
    public String toString()
    {
        String res="";
        int n_total = getEntries();
        int nprint = 0;
        for (int i=0;i<values.length && nprint<10;i++) {
            double rate = getIntegral(i, 256) / (1.0*n_total);
            if (rate == 1.0) { continue; }
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
        max_bin = 0;
    }

    // fill a new entry in the histogram, for the given value of the histogram variable.
    // optionally, a floating-point weight may be added.
    public void fill(int val) {
        fill(val, 1);
    }
    public void fill(int x, double weight) {
        // keep track of the total number of raw entries filled
        entries += 1;

        // keep track of the highest non-zero bin
        if (x > max_bin && x < nbins) {
            max_bin = x;
        }

        // underflow/overflow entries get added to the end of the normal array
        // they do not contribute to the integral or other stats, and are not
        // included for iteration
        if (x < 0) {
            // underflow
            values[nbins] += weight;
            if (doErrors) {
                double lastval = errors[nbins];
                errors[nbins] = Math.sqrt(weight * weight + lastval * lastval);
            }
        }
        else if (x >= nbins) {
            // overflow
            values[nbins+1] += weight;
            if (doErrors) {
                double lastval = errors[nbins+1];
                errors[nbins+1] = Math.sqrt(weight * weight + lastval * lastval);
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
        if (mean_valid)
            return mean;

        int sum = 0;
        for (int i = 0; i < nbins; ++i) {
            sum += values[i];
        }
        mean = ((double) sum) / integral;
        mean_valid = true;
        return mean;
    }

    // calculate the variance of the histogram variable
    public double getVariance() {
        // if the variance has not been invalidated since the last
        // time we calculated it, just return the previous value.
        if (variance_valid)
            return variance;

        mean = getMean();
        int sum = 0;
        for (int i = 0; i < nbins; ++i) {
            double val = values[i];
            sum += (val-mean)*(val-mean);
        }
        variance = ((double) sum) / integral;
        variance_valid = true;
        return variance;
    }

    // return the maximum non-zero bin.
    public int getMaxBin() { return max_bin; }

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
    public double getUnderflow() { return values[nbins]; }
    public double getOverflow() { return values[nbins+1]; }

    // get the value of a specific bin. returns 0 for any bins outside the range.
    public double getBinValue(int i) {
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
    public int getEntries() { return entries; }

    // get an iterator over the bin contents. iterator does not include overflow/underflow bins.
    public Iterator<Integer> iterator() { return new HistogramIterator(); }

    // get a copy of the whole array of bin values.
    public int[] getValues() { return values.clone(); }
}