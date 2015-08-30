package edu.uci.crayfis;

import java.text.DecimalFormat;

/**
 * Created by jodi on 2015-06-03.
 */
public final class ViewUtil {

    private static final DecimalFormat POINT_FORMATTER = new DecimalFormat("0.0E+0");

    private ViewUtil() {

    }

    /**
     * Get the scientific notation for the given value.
     *
     * @param value The input value.
     * @return Formatted scientific notiation.
     */
    public static String formatDecimal(final int value) {
        return POINT_FORMATTER.format(value);
    }

    /**
     * Get the scientific notation for the given value.
     *
     * @param value The input value.
     * @return Formatted scientific notiation.
     */
    public static String formatDecimal(final long value) {
        return POINT_FORMATTER.format(value);
    }

}
