package edu.uci.crayfis.widget;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.TextView;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;

/**
 * Show the user the status of the application.
 */
public final class StatusView extends TextView {

    private static final CFConfig CONFIG = CFConfig.getInstance();

    public StatusView(final Context context) {
        super(context);
    }

    public StatusView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }


    public void setStatus(int time, int fps) {
        final CFApplication application = (CFApplication) getContext().getApplicationContext();
        final CFApplication.State currentState = application.getApplicationState();

        String text;
        if (currentState == CFApplication.State.DATA) {
             text = String.format("Current Run Time: %ds\n" +
                            "Frame rate: %d f.p.s.\n",time,
                    fps);
        } else {
            text = String.format("<Preparing for run>\nFrame rate: %d f.p.s.",
                    fps);

        }
        if (CONFIG.getAccountName() != null) {
            text += String.format("\n\n Device: %s\nAccount: %s\n Score: %1.1f\n",CONFIG.getDeviceNickname(),CONFIG.getAccountName(),CONFIG.getAccountScore());
        }
        else
        {
            if (currentState == CFApplication.State.DATA) {
                text += String.format("\n\n Sign in and register this device to earn credits for prizes and be an author on scientific papers!");
            }

        }
        setText(text);
    }

}
