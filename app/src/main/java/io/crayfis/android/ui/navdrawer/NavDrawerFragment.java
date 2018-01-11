package io.crayfis.android.ui.navdrawer;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Base fragment that handles UI updates.
 */
public abstract class NavDrawerFragment extends Fragment {

    private static final long UI_UPDATE_TICK_MS = 1000L;

    @Nullable
    private Handler mUiHandler;
    @Nullable
    private Timer mTimer;

    protected void startUiUpdate() {

        if (mUiHandler == null) {
            mUiHandler = new Handler(Looper.getMainLooper());
        }

        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = new Timer("UI Update Timer");
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        update();
                    }
                });
            }
        }, 0L, UI_UPDATE_TICK_MS);
    }

    @Override
    public void onResume() {
        super.onResume();
        startUiUpdate();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    // specific methods to be overridden by fragment classes

    /**
     * Returns the appropriate ID to be displayed when the About tab is clicked
     *
     * @return ID from R.string
     */
    public abstract @StringRes int about();

    /**
     * Method to be called during UI Update Timer tick
     */
    public void update() {}
}
