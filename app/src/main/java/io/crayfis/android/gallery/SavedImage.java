package io.crayfis.android.gallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import io.crayfis.android.util.CFLog;


import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;

import io.crayfis.android.trigger.L2Task;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by danielwhiteson on 11/20/14.
 */
public class SavedImage {
    public String filename;
    public int max_pix;
    public int num_pix;
    public String date;
    public Bitmap bitmap;

    public String makeFilename(int mp,int np, String d)
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

    public SavedImage(ArrayList<L2Task.RecoPixel> pixels,int max,int width,int height, long t)
    {

        max_pix=max;
        num_pix=pixels.size();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy-HH:mm");
        Date resultdate = new Date(t);
        date=sdf.format(resultdate);
        filename=makeFilename(max, num_pix, date);

        // make the bitmap

        // Get bounding box
        int maxx = 0;
        int maxy = 0;
        int minx = width - 1;
        int miny = height - 1;

        for (int i = 0; i < pixels.size(); i++) {
            L2Task.RecoPixel pix = pixels.get(i);

            int x = pix.getX();
            int y = pix.getY();

            CFLog.d(" pixel at x,y=" + x + "," + y);
            if (x < minx) minx = x;
            if (x > maxx) maxx = x;

            if (y < miny) miny = y;
            if (y > maxy) maxy = y;
        }


        CFLog.d(" bounding box: x=[" + minx + " - " + maxx + "] y=[" + miny + " - " + maxy + "] max=" + max);
        // add a buffer so we don't get super tiny images
        int delta = 25;
        maxx = java.lang.Math.min(maxx + delta, width);
        maxy = java.lang.Math.min(maxy + delta, height);
        minx = java.lang.Math.max(minx - delta, 0);
        miny = java.lang.Math.max(miny - delta, 0);
        CFLog.d(" bounding box: x=[" + minx + " - " + maxx + "] y=[" + miny + " - " + maxy + "] max=" + max);
        try {

            bitmap = Bitmap.createBitmap(maxx - minx, maxy - miny, Bitmap.Config.RGB_565);

            if (bitmap != null) {
                // put all pixels in the bitmap
                for (int i = 0; i < pixels.size(); i++) {
                    L2Task.RecoPixel pix = pixels.get(i);
                    int x = pix.getX() - minx;
                    int y = pix.getY() - miny;
                    int val = (int) (255 * (pix.getVal() / (1.0 * max)));
                    int argb = 0xFF000000 | (val << 4) | (val << 2) | val;
                    CFLog.d(" pixel at x,y=" + x + "," + y + " = " + val + " argb=" + argb);

                    bitmap.setPixel(x, y, Color.argb(255, val, val, val));

                }
            }
        }  catch (Exception e) {
            Crashlytics.logException(e);
            e.printStackTrace();
        }
        CFLog.d("Success building image bitmap="+bitmap+" filename="+filename);
    }

    public SavedImage(String fname) {

        filename = fname;
        bitmap = BitmapFactory.decodeFile(fname);
        decodeFilename(fname);

    }


}