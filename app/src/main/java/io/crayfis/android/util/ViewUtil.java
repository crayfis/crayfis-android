package io.crayfis.android.util;

import java.text.DecimalFormat;

/**
 * Created by jodi on 2015-06-03.
 */
public final class ViewUtil {

    private static final DecimalFormat POINT_FORMATTER_SCIENTIFIC = new DecimalFormat("0.0E+0");
    private static final DecimalFormat POINT_FORMATTER = new DecimalFormat("#");

    static {
        POINT_FORMATTER.setGroupingUsed(true);
        POINT_FORMATTER.setGroupingSize(3);
    }

    private ViewUtil() {

    }

    /**
     * Get the scientific notation for the given value.
     *
     * @param value The input value.
     * @return Formatted scientific notiation.
     */
    public static String formatDecimalScientific(final int value) {
        return POINT_FORMATTER_SCIENTIFIC.format(value);
    }

    /**
     * Get the scientific notation for the given value.
     *
     * @param value The input value.
     * @return Formatted scientific notiation.
     */
    public static String formatDecimalScientific(final long value) {
        return POINT_FORMATTER_SCIENTIFIC.format(value);
    }

    /**
     * Get the comma separated value notation for the given value.
     *
     * @param value The input value.
     * @return Formatted value.
     */
    public static String formatDecimal(final int value) {
        return POINT_FORMATTER.format(value);
    }

    /**
     * Get the comma separated value notation for the given value.
     *
     * @param value The input value.
     * @return Formatted value.
     */
    public static String formatDecimal(final long value) {
        return POINT_FORMATTER.format(value);
    }
}
