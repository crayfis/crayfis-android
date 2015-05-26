package edu.uci.crayfis.navdrawer;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import edu.uci.crayfis.R;

/**
 * Adapter for populating the navigation drawer.
 */
//TODO: The arrays are not translated.
public final class NavDrawerAdapter extends ArrayAdapter<NavDrawerAdapter.Type> {

    /**
     * The navigation type, used to tag the nav drawer entry.
     */
    public enum Type {
        DEVELOPER(0),
        LIVE_VIEW(1),
        STATUS(2),
        YOUR_LEVEL(3),
        DATA(4),
        NETWORK_MAP(5),
        YOUR_ACCOUNT(6),
        DOSIMETER(7),
        FEEDBACK(8),
        GALLERY(9);

        private final int mIndex;

        Type(final int index) {
            mIndex = index;
        }

        /**
         * Get the index of this type.  Typically this is used to index the string array for page titles.
         *
         * @return The index of this page.
         */
        public int getIndex() {
            //FIXME: When developer mode is toggleable, this needs to return n-1 when developer mode is off.
            return mIndex;
        }
    }

    private static final Type[] NAV_ENTRIES = new Type[] {
            Type.DEVELOPER,
            Type.LIVE_VIEW,
            Type.STATUS,
            Type.YOUR_LEVEL,
            Type.DATA,
            Type.NETWORK_MAP,
            Type.YOUR_ACCOUNT,
            Type.DOSIMETER,
            Type.FEEDBACK,
            Type.GALLERY
    };

    public NavDrawerAdapter(final Context context) {
        super(context, 0, 0, NAV_ENTRIES);
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
        rtn.setText(NavHelper.getNavTitle(getContext().getResources(), type));
        rtn.setTag(type);
        return rtn;
    }
}
