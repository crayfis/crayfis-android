package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import edu.uci.crayfis.util.CFLog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
        import android.support.v4.app.FragmentManager;
        import android.support.v4.app.FragmentPagerAdapter;

public class ViewPagerAdapter extends FragmentPagerAdapter {
    private Context _context;

    private boolean developerMode=false;

    public void setDeveloperMode(boolean m) {
        boolean prev = developerMode;
        developerMode = m;
        if (m != prev) notifyDataSetChanged();
    }


    public ViewPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        developerMode=true;
        _context=context;

    }

    private static String[] Titles = {"Developer","Status","Data","Network Map","Your Account","Realtime","Gallery"};

    public static final int DEVELOPER = 0;
    public static final int STATUS = 1;
    public static final int DATA = 2;
    public static final int LEADER = 3;
    public static final int LOGIN = 4;
    public static final int DOSIMETER = 5;
    public static final int GALLERY = 6;

    @Override
    public CharSequence getPageTitle(int position) {
        return Titles[position];
    }

    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch(position) {
            case STATUS:
                f = LayoutData.getInstance();
                break;
            case DATA:
                f = LayoutHist.getInstance();
                break;
            case DOSIMETER:
                f = LayoutTime.getInstance();
                break;
            case GALLERY:
                f = LayoutGallery.getInstance();
                break;
            case DEVELOPER:
                f = LayoutDeveloper.getInstance();
                break;
            case LEADER:
                f = LayoutLeader.getInstance();
                break;
            case LOGIN:
                f = LayoutLogin.getInstance();
                break;
        }

        return f;
    }

    @Override
    public int getCount() {

        int this_count=GALLERY;
        if (developerMode==true)
          this_count=GALLERY+1;

        //CFLog.d(" ViewPagerAdapter: developer mode = "+developerMode+" this_count = "+this_count);

        return this_count;

    }

}