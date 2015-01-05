package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.content.Context;
        import android.support.v4.app.Fragment;
        import android.support.v4.app.FragmentManager;
        import android.support.v4.app.FragmentPagerAdapter;

public class ViewPagerAdapter extends FragmentPagerAdapter {
    private Context _context;

    public boolean developerMode;

    public ViewPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        developerMode=true;
        _context=context;

    }

    private static String[] Titles = {"Status","Data","Leaderboard","Dosimeter","Gallery","Developer"};
    public static final int STATUS = 0;
    public static final int DATA = 1;
    public static final int LEADER = 2;
    public static final int DOSIMETER = 3;
    public static final int GALLERY = 4;
    public static final int DEVELOPER = 5;

    @Override
    public CharSequence getPageTitle(int position) {
        return Titles[position];
    }


    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch(position){
            case STATUS:
                f=LayoutData.getInstance();
                break;
            case DATA:
                f=LayoutHist.getInstance();
                break;
            case DOSIMETER:
                f=LayoutTime.getInstance();
                break;
            case GALLERY:
                f=LayoutGallery.getInstance();
                break;
            case DEVELOPER:
                f=LayoutDeveloper.getInstance();
                    break;
            case LEADER:
                f=LayoutLeader.getInstance();
                break;
               }

        return f;
    }
    @Override
    public int getCount() {

       return DEVELOPER+1;

    }

}