package io.crayfis.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.crayfis.android.R;

/**
 * A collection of utilities.
 */
public final class CFUtil {

    private CFUtil() {

    }

    /**
     * Check to see if the activity is still valid.  Mainly useful for callbacks.
     *
     * @param activity The activity.
     * @return {@code true} if still valid, {@code false} if not.
     */
    public static boolean isActivityValid(@Nullable final Activity activity) {
        return (activity != null && ! activity.isFinishing());
    }

    /**
     * Create a new generic fatal error dialog.  The activity passed will be finished when the dialog
     * is closed.
     *
     * @param activity The activity.
     * @return A new {@link AlertDialog}.  You must call {@code show()} on it.
     */
    @Nullable
    public static AlertDialog newFatalErrorDialog(@NonNull final Activity activity) {
        if (! isActivityValid(activity)) {
            return null;
        }

        final Dialog.OnClickListener closeListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, final int i) {
                activity.finish();
            }
        };

        return new AlertDialog.Builder(activity)
                .setTitle(R.string.fatal_error_title)
                .setMessage(R.string.fatal_error_message)
                .setCancelable(false)
                .setNeutralButton("Close Application", closeListener)
                .create();
    }
}
