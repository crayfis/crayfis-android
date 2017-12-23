package io.crayfis.android.navdrawer;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import io.crayfis.android.ui.LayoutBlack;
import io.crayfis.android.ui.LayoutDeveloper;
import io.crayfis.android.ui.LayoutFeedback;
import io.crayfis.android.ui.LayoutGallery;
import io.crayfis.android.ui.LayoutHist;
import io.crayfis.android.ui.LayoutLeader;
import io.crayfis.android.ui.LayoutLogin;
import io.crayfis.android.ui.LayoutTime;
import io.crayfis.android.R;
import io.crayfis.android.ui.DataCollectionFragment;
import io.crayfis.android.util.CFLog;

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
     * The view must be tagged with {@link io.crayfis.android.navdrawer.NavDrawerAdapter.Type} for this to do anything.
     * This will make a call to {@link #setFragment(AppCompatActivity, Fragment, CharSequence)} on a successful match.
     *
     * @param activity {@link AppCompatActivity}
     * @param navItem {@link Fragment}
     * @param drawerListener Optional {@link io.crayfis.android.navdrawer.NavHelper.NavDrawerListener}.  If set, the fragment will be set after the drawer closes.
     */
    public static void doNavClick(@NonNull final AppCompatActivity activity, @NonNull final View navItem,
                                  @Nullable final NavDrawerListener drawerListener, @Nullable final DrawerLayout drawerLayout) {
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
                    newFragment = new DataCollectionFragment();
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
                case GALLERY:
                    newFragment = LayoutGallery.getInstance();
                    break;
                default:
                    newFragment = null;
            }

            if (newFragment == null) {
                CFLog.e("Unhandled navigation type " + type);
            } else if (currentFragment == null || !(currentFragment.getClass().isInstance(newFragment))) {
                if (drawerListener != null && drawerLayout != null) {
                    drawerListener.setFragmentOnClose(activity, newFragment, type.getTitle());
                    drawerLayout.closeDrawers();
                } else {
                    setFragment(activity, newFragment, type.getTitle());
                }

            } else if (drawerLayout != null) {
                drawerLayout.closeDrawers();
            }
        }
    }

    /**
     * Implementation of {@link android.support.v4.widget.DrawerLayout.DrawerListener} that will set the fragment
     * after closing.
     */
    public static final class NavDrawerListener implements DrawerLayout.DrawerListener {

        @Nullable
        private final ActionBarDrawerToggle mActionBarDrawerToggle;

        @Nullable
        private NavWrapper onCloseFragment;

        public NavDrawerListener(@Nullable ActionBarDrawerToggle actionBarDrawerToggle) {
            mActionBarDrawerToggle = actionBarDrawerToggle;
        }

        @Override
        public void onDrawerSlide(final View drawerView, final float slideOffset) {
            if (mActionBarDrawerToggle != null) {
                mActionBarDrawerToggle.onDrawerSlide(drawerView, slideOffset);
            }
        }

        @Override
        public void onDrawerOpened(final View drawerView) {
            if (mActionBarDrawerToggle != null) {
                mActionBarDrawerToggle.onDrawerOpened(drawerView);
            }
        }

        @Override
        public void onDrawerClosed(final View drawerView) {
            if (mActionBarDrawerToggle != null) {
                mActionBarDrawerToggle.onDrawerClosed(drawerView);
            }

            if (onCloseFragment != null) {
                setFragment(onCloseFragment.app_compat_activity, onCloseFragment.fragment, onCloseFragment.title);
                onCloseFragment = null;
            }
        }

        @Override
        public void onDrawerStateChanged(final int newState) {
            if (mActionBarDrawerToggle != null) {
                mActionBarDrawerToggle.onDrawerStateChanged(newState);
            }
        }

        /**
         * Set the fragment to load when the drawer is closed.
         *
         * This should be done through a click handler on a navigation drawer item.
         *
         * @param appCompatActivity The {@link AppCompatActivity}
         * @param fragment The {@link Fragment}
         * @param title The optional title.
         */
        public void setFragmentOnClose(@NonNull final AppCompatActivity appCompatActivity, @NonNull final Fragment fragment,
                                       @Nullable final CharSequence title) {
            onCloseFragment = new NavWrapper(appCompatActivity, fragment, title);
        }

        /**
         * Private POJO wrapper to use in {@link #onDrawerClosed(View)}.
         */
        private static final class NavWrapper {
            private final AppCompatActivity app_compat_activity;
            private final Fragment fragment;
            private final CharSequence title;


            private NavWrapper(final AppCompatActivity app_compat_activity, final Fragment fragment, final CharSequence title) {
                this.app_compat_activity = app_compat_activity;
                this.fragment = fragment;
                this.title = title;
            }
        }
    }
}