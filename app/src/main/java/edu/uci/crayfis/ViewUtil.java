package edu.uci.crayfis;

import java.text.DecimalFormat;

/**
 * Created by jodi on 2015-06-03.
 */
public final class ViewUtil {

    private static final DecimalFormat POINT_FORMATTER = new DecimalFormat("#");

    static {
        POINT_FORMATTER.setGroupingUsed(true);
        POINT_FORMATTER.setGroupingSize(3);
    }

    private ViewUtil() {

    }

    public static String formatDecimal(final int decimal) {
        return POINT_FORMATTER.format(decimal);
    }
}
