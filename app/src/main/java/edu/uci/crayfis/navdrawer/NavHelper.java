package edu.uci.crayfis.navdrawer;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import edu.uci.crayfis.LayoutBlack;
import edu.uci.crayfis.LayoutData;
import edu.uci.crayfis.LayoutDeveloper;
import edu.uci.crayfis.LayoutFeedback;
import edu.uci.crayfis.LayoutHist;
import edu.uci.crayfis.LayoutLeader;
import edu.uci.crayfis.LayoutLevels;
import edu.uci.crayfis.LayoutLogin;
import edu.uci.crayfis.LayoutTime;
import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by jodi on 2015-05-25.
 */
public final class NavHelper {

    private NavHelper() {}

    /**
     * Helper for setting the fragment.
     *
     * This expects that a ViewGroup with the id {@code fragment_container} is available.  If not, this does
     * nothing.
     *
     * @param activity {@link AppCompatActivity}.
     * @param fragment The {@link Fragment}.
     */
    public static void setFragment(@NonNull final AppCompatActivity activity, @NonNull final Fragment fragment,
                                   @Nullable final CharSequence title) {
        final ViewGroup container = (ViewGroup) activity.findViewById(R.id.fragment_container);
        if (container != null) {
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            activity.setTitle(title);
        }
    }

    /**
     * Helper for handling a navigation drawer click.
     *
     * The view must be tagged with {@link edu.uci.crayfis.navdrawer.NavDrawerAdapter.Type} for this to do anything.
     * This will make a call to {@link #setFragment(AppCompatActivity, Fragment, CharSequence)} on a successful match.
     *
     * @param activity {@link AppCompatActivity}
     * @param navItem {@link Fragment}
     */
    public static void doNavClick(@NonNull final AppCompatActivity activity, @NonNull final View navItem) {
        final NavDrawerAdapter.Type type = (NavDrawerAdapter.Type) navItem.getTag();
        if (type != null) {
            final List fragments = activity.getSupportFragmentManager().getFragments();
            final Fragment currentFragment = (fragments.size() > 0) ? (Fragment) fragments.get(0) : null;
            final Fragment newFragment;

            switch (type) {
                case DEVELOPER:
                    newFragment = LayoutDeveloper.getInstance();
                    break;
                case LIVE_VIEW:
                    newFragment = LayoutBlack.getInstance();
                    break;
                case STATUS:
                    newFragment = LayoutData.getInstance();
                    break;
                case YOUR_LEVEL:
                    newFragment = LayoutLevels.getInstance();
                    break;
                case DATA:
                    newFragment = LayoutHist.getInstance();
                    break;
                case NETWORK_MAP:
                    newFragment = LayoutLeader.getInstance();
                    break;
                case YOUR_ACCOUNT:
                    newFragment = LayoutLogin.getInstance();
                    break;
                case DOSIMETER:
                    newFragment = LayoutTime.getInstance();
                    break;
                case FEEDBACK:
                    newFragment = LayoutFeedback.getInstance();
                    break;
                default:
                    newFragment = null;
            }

            if (newFragment == null) {
                CFLog.e("Unhandled navigation type " + type);
            } else if (currentFragment == null || !(currentFragment.getClass().isInstance(newFragment))) {
                final String[] titles = activity.getResources().getStringArray(R.array.pager_titles);
                setFragment(activity, newFragment, titles[type.getIndex()]);
            }
        }
    }
}
