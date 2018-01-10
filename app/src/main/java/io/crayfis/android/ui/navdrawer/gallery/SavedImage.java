package io.crayfis.android.ui.navdrawer.gallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Pair;

import io.crayfis.android.DataProtos;
import io.crayfis.android.util.CFLog;


import com.crashlytics.android.Crashlytics;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by danielwhiteson on 11/20/14.
 */
class SavedImage {
    String filename;
    int max_pix;
    int num_pix;
    String date;
    Bitmap bitmap;

    private String makeFilename(int mp,int np, String d)
    {
        return "event_mp_"+mp+"_np_"+np+"_date_"+d+".png";
    }

    private void decodeFilename(String filename)
    {
        String delims1 = "[\\.]";
        String[] fn1 = filename.split(delims1);
        max_pix=-1;
        num_pix=-1;
        date="???";
        if (fn1.length>1) {
            String delims2 = "_";
            String[] tokens = fn1[1].split(delims2);
            if (tokens.length>6) {
                max_pix = Integer.valueOf(tokens[2]);
                num_pix = Integer.valueOf(tokens[4]);
                date = tokens[6];
                CFLog.d("SavedImage: input name=" + filename + " mp = " + max_pix + " np = " + num_pix + " date= [" + date + "]");
            }
        }
    }

    private SavedImage(long t) {

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy-HH:mm");
        Date resultdate = new Date(t);
        date=sdf.format(resultdate);
    }

    SavedImage(DataProtos.ByteBlock bb, long t) {
        // first generate the date
        this(t);

        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        int maxx = 0;
        int maxy = 0;

        for(int i=0; i<bb.getXCount(); i++) {

            int x = bb.getX(i);
            int y = bb.getY(i);

            CFLog.d(" pixel at x,y=" + x + "," + y);
            if (x < minx) minx = x;
            if (x > maxx) maxx = x;

            if (y < miny) miny = y;
            if (y > maxy) maxy = y;
        }

        for(int val: bb.getValList()) {
            if (val > max_pix) max_pix = val;
        }

        num_pix = bb.getXCount();

        // add a buffer so we don't get super tiny images
        int minSide = 50;
        int deltaX = Math.max(minSide - (maxx - minx), 0) / 2;
        int deltaY = Math.max(minSide - (maxy - miny), 0) / 2;
        maxx = maxx + deltaX;
        maxy = maxy + deltaY;
        minx = minx - deltaX;
        miny = miny - deltaY;
        CFLog.d(" bounding box: x=[" + minx + " - " + maxx + "] y=[" + miny + " - " + maxy + "] max=" + max_pix);
        try {

            bitmap = Bitmap.createBitmap(maxx - minx, maxy - miny, Bitmap.Config.RGB_565);

            if (bitmap != null) {
                // put all pixels in the bitmap
                Iterator<Integer> valIterator = bb.getValList().iterator();
                int r = (bb.getSideLength()-1)/2;
                LinkedHashSet<Pair<Integer, Integer>> blockXY = new LinkedHashSet<>();

                for (int i = 0; i < bb.getXCount(); i++) {
                    int x = bb.getX(i) - minx;
                    int y = bb.getY(i) - miny;

                    for(int dy=Math.max(y-r,0); dy<=Math.min(y+r, maxy-miny-1); dy++) {
                        for(int dx=Math.max(x-r,0); dx<=Math.min(x+r, maxx-minx-1); dx++) {
                            blockXY.add(Pair.create(x, y));
                        }
                    }

                }

                for(Pair<Integer, Integer> xy : blockXY) {
                    int val = (int) (255 * (valIterator.next() / (1.0 * max_pix)));
                    int argb = 0xFF000000 | (val << 4) | (val << 2) | val;
                    CFLog.d(" pixel at x,y=" + xy.first + "," + xy.second + " = " + val + " argb=" + argb);

                    bitmap.setPixel(xy.first, xy.second, Color.argb(255, val, val, val));
                }
            }
        }  catch (Exception e) {
            Crashlytics.logException(e);
            e.printStackTrace();
        }
        filename=makeFilename(max_pix, num_pix, date);
        CFLog.d("Success building image bitmap="+bitmap+" filename="+filename);
    }

    SavedImage(List<DataProtos.Pixel> pixels, long t)
    {
        // first generate the date
        this(t);

        num_pix=pixels.size();

        // make the bitmap

        // find the bounds and max value
        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        int maxx = 0;
        int maxy = 0;

        for (int i = 0; i < pixels.size(); i++) {
            DataProtos.Pixel pix = pixels.get(i);

            int x = pix.getX();
            int y = pix.getY();
            int val = pix.getVal();

            CFLog.d(" pixel at x,y=" + x + "," + y);
            if (x < minx) minx = x;
            if (x > maxx) maxx = x;

            if (y < miny) miny = y;
            if (y > maxy) maxy = y;

            if (val > max_pix) max_pix = val;
        }

        // add a buffer so we don't get super tiny images
        int minSide = 30;
        int deltaX = Math.max(minSide - (maxx - minx), 0) / 2;
        int deltaY = Math.max(minSide - (maxy - miny), 0) / 2;
        maxx = maxx + deltaX;
        maxy = maxy + deltaY;
        minx = minx - deltaX;
        miny = miny - deltaY;
        CFLog.d(" bounding box: x=[" + minx + " - " + maxx + "] y=[" + miny + " - " + maxy + "] max=" + max_pix);
        try {

            bitmap = Bitmap.createBitmap(maxx - minx, maxy - miny, Bitmap.Config.RGB_565);

            if (bitmap != null) {
                // put all pixels in the bitmap
                for (int i = 0; i < pixels.size(); i++) {
                    DataProtos.Pixel pix = pixels.get(i);
                    int x = pix.getX() - minx;
                    int y = pix.getY() - miny;
                    int val = (int) (255 * (pix.getVal() / (1.0 * max_pix)));
                    int argb = 0xFF000000 | (val << 4) | (val << 2) | val;
                    CFLog.d(" pixel at x,y=" + x + "," + y + " = " + val + " argb=" + argb);

                    bitmap.setPixel(x, y, Color.argb(255, val, val, val));

                }
            }
        }  catch (Exception e) {
            Crashlytics.logException(e);
            e.printStackTrace();
        }
        filename=makeFilename(max_pix, num_pix, date);
        CFLog.d("Success building image bitmap="+bitmap+" filename="+filename);
    }

    SavedImage(String fname) {

        filename = fname;
        bitmap = BitmapFactory.decodeFile(fname);
        decodeFilename(fname);

    }


}