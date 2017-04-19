package edu.uci.crayfis.ui;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Base fragment that handles UI updates.
 */
public class CFFragment extends Fragment {

    private static final long UI_UPDATE_TICK_MS = 1000L;

    @Nullable
    private Handler mUiHandler;
    @Nullable
    private Timer mTimer;
    @Nullable
    private Runnable mUiRunnable;

    protected void startUiUpdate(@NonNull final Runnable runnable) {
        mUiRunnable = runnable;

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
                mUiHandler.post(runnable);
            }
        }, 0L, UI_UPDATE_TICK_MS);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mUiRunnable != null) {
            startUiUpdate(mUiRunnable);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }
}
