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

    public ViewPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        _context=context;

    }
    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch(position){
            case 0:
                f=LayoutData.getInstance();
                break;
            case 1:
                f=LayoutHist.getInstance(_context);
                break;
            case 2:
                f=LayoutTime.getInstance(_context);
                break;
            case 3:
                f=LayoutGallery.getInstance(_context);
                break;
        }
        return f;
    }
    @Override
    public int getCount() {
        return 4;
    }

}