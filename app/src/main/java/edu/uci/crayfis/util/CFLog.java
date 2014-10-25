package edu.uci.crayfis.util;

import android.util.Log;

/**
 * A standard logging class that all logging statements should go through.<br/>
 *<br/>
 * This provides a uniform way of enabling, disabling and finding logging statements through the {@link #TAG} that are
 * related to the app.
 *
 * TODO Migrate this to dependency injection
 * TODO Make logging configurable through a centralized configuration
 */
public final class CFLog {

    private static final boolean ENABLED = true;
    private static final String TAG = "CRAYFIS";

    private CFLog() {
        // Everything is static.
    }

    /**
     * {@link Log#VERBOSE} logging statement.
     *
     * @param message The message to log.
     */
    public static void v(final String message) {
        if (ENABLED) {
            Log.v(TAG, message);
        }
    }

    /**
     * {@link Log#VERBOSE} logging statement.
     *
     * @param message The message to log.
     * @param throwable The throwable.
     */
    public static void v(final String message, final Throwable throwable) {
        if (ENABLED) {
            Log.v(TAG, message, throwable);
        }
    }

    /**
     * {@link Log#DEBUG} logging statement.
     *
     * @param message The message to log.
     */
    public static void d(final String message) {
        if (ENABLED) {
            Log.d(TAG, message);
        }
    }

    /**
     * {@link Log#DEBUG} logging statement.
     *
     * @param message The message to log.
     * @param throwable The throwable.
     */
    public static void d(final String message, final Throwable throwable) {
        if (ENABLED) {
            Log.d(TAG, message, throwable);
        }
    }

    /**
     * {@link Log#INFO} logging statement.
     *
     * @param message The message to log.
     */
    public static void i(final String message) {
        if (ENABLED) {
            Log.i(TAG, message);
        }
    }

    /**
     * {@link Log#INFO} logging statement.
     *
     * @param message The message to log.
     * @param throwable The throwable.
     */
    public static void i(final String message, final Throwable throwable) {
        if (ENABLED) {
            Log.i(TAG, message, throwable);
        }
    }

    /**
     * {@link Log#WARN} logging statement.
     *
     * @param message The message to log.
     */
    public static void w(final String message) {
        if (ENABLED) {
            Log.w(TAG, message);
        }
    }

    /**
     * {@link Log#WARN} logging statement.
     *
     * @param message The message to log.
     * @param throwable The throwable.
     */
    public static void w(final String message, final Throwable throwable) {
        if (ENABLED) {
            Log.w(TAG, message, throwable);
        }
    }

    /**
     * {@link Log#ERROR} logging statement.
     *
     * @param message The message to log.
     */
    public static void e(final String message) {
        if (ENABLED) {
            Log.e(TAG, message);
        }
    }

    /**
     * {@link Log#ERROR} logging statement.
     *
     * @param message The message to log.
     * @param throwable The throwable.
     */
    public static void e(final String message, final Throwable throwable) {
        if (ENABLED) {
            Log.e(TAG, message, throwable);
        }
    }

}
