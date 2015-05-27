package edu.uci.crayfis.navdrawer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.uci.crayfis.BuildConfig;
import edu.uci.crayfis.R;

/**
 * Adapter for populating the navigation drawer.
 *
 * This will dynamically adapt itself if the developer mode should be shown or if the gallery should be shown.  Right
 * now, developer view is linked with {@link BuildConfig#DEBUG}.
 *
 * For the navigation drawer to update, you will need to call notifyDataSetChanged or recreate the adapter.
 */
public final class NavDrawerAdapter extends ArrayAdapter<NavDrawerAdapter.Type> {

    /**
     * The navigation type, used to tag the nav drawer entry.
     * TODO The navigation titles were never translated.  When that happens, refactor this to use the resource id instead.
     */
    public enum Type {
        DEVELOPER("Developer"),
        LIVE_VIEW("Live View"),
        STATUS("Status"),
        YOUR_LEVEL("Your Level"),
        DATA("Data"),
        NETWORK_MAP("Network Map"),
        YOUR_ACCOUNT("Your Account"),
        DOSIMETER("Dosimeter"),
        FEEDBACK("Feedback"),
        GALLERY("Gallery");

        private final String mTitle;

        Type(final String title) {
            mTitle = title;
        }

        /**
         * Get the title of this type.
         *
         * @return The title of this navigation type.
         */
        public String getTitle() {
            //FIXME: When developer mode is toggleable, this needs to return n-1 when developer mode is off.
            return mTitle;
        }
    }

    private static final Type[] BASE_NAV_ENTRIES = new Type[] {
            Type.LIVE_VIEW,
            Type.STATUS,
            Type.YOUR_LEVEL,
            Type.DATA,
            Type.NETWORK_MAP,
            Type.YOUR_ACCOUNT,
            Type.DOSIMETER,
            Type.FEEDBACK
    };

    public NavDrawerAdapter(final Context context) {
        super(context, 0);
    }

    @Override
    public int getCount() {
        int count = BASE_NAV_ENTRIES.length;

        if (hasDeveloper()) {
            count++;
        }
        if (hasGallery()) {
            count++;
        }
        return count;
    }

    @Override
    public Type getItem(final int position) {
        final List<Type> items = new ArrayList<>(Arrays.asList(BASE_NAV_ENTRIES));
        if (hasGallery()) {
            items.add(Type.GALLERY);
        }
        if (hasDeveloper()) {
            items.add(Type.DEVELOPER);
        }

        return items.get(position);
    }

    /**
     * Check if the nav drawer should have the developer view.
     *
     * @return {@code true} or {@code false}.
     */
    private boolean hasDeveloper() {
        return BuildConfig.DEBUG;
    }

    /**
     * Check if the nav drawer should have the gallery view.
     *
     * @return {@code true} or {@code false}.
     */
    private boolean hasGallery() {
        final Context context = getContext();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String galleryKey = context.getString(R.string.prefEnableGallery);
        return preferences.getBoolean(galleryKey, false);
    }

    /**
     * Get the view for the drawer.
     * 
     * These views are tagged with their type
     * @param position
     * @param convertView
     * @param parent
     * @return
     */
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final TextView rtn = (convertView != null && convertView instanceof TextView)
                ? (TextView) convertView
                : new TextView(new ContextThemeWrapper(getContext(), R.style.NavDrawerItem), null, 0);

        final Type type = getItem(position);
        rtn.setText(type.getTitle());
        rtn.setTag(type);
        return rtn;
    }
}
