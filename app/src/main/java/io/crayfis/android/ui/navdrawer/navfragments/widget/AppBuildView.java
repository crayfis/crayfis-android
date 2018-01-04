package io.crayfis.android.ui.navdrawer.navfragments.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import io.crayfis.android.main.CFApplication;

/**
 * Show the user information related to this build and run.
 */
public final class AppBuildView extends AppCompatTextView {
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
