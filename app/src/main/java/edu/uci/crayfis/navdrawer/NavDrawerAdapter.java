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
public final class NavDrawerAdapter extends ArrayAdapter<Object> {

    private String[] mTitles;

    public NavDrawerAdapter(final Context context) {
        super(context, 0);
        mTitles = context.getResources().getStringArray(R.array.pager_titles);
    }

    @Override
    public int getCount() {
        // FIXME: Developer mode should be a toggable option.
        //
        // In the previously existing ViewPagerAdapter the developer mode was hard coded.  This should
        // instead be bound to a build configuration setting.  Could use BuildConfig.DEBUG but not sure
        // if debug builds are being sent out for beta testing.
        return 10;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final TextView rtn = (convertView != null && convertView instanceof TextView)
                ? (TextView) convertView
                : new TextView(new ContextThemeWrapper(getContext(), R.style.NavDrawerItem), null, 0);
        rtn.setText(mTitles[position]);
        return rtn;
    }
}
