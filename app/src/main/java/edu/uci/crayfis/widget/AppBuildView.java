package edu.uci.crayfis.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.TextView;

import edu.uci.crayfis.CFApplication;

/**
 * Show the user information related to this build and run.
 */
public final class AppBuildView extends TextView {
    public AppBuildView(final Context context) {
        super(context);
    }

    public AppBuildView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public AppBuildView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAppBuild(@NonNull final CFApplication.AppBuild appBuild) {
        final String text = String.format("Device ID: %s, Run ID: %s, Version %s",
                appBuild.getDeviceId(),
                appBuild.getRunId().toString().substring(19),
                appBuild.getBuildVersion());
        setText(text);
    }
}
