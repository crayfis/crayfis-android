package io.crayfis.android.ui.navdrawer.gallery;

/**
 * Created by danielwhiteson on 11/19/14.
 */


import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.crayfis.android.DataProtos;
import io.crayfis.android.util.CFLog;

import android.content.Context;
import android.graphics.Point;
import android.os.Environment;
import android.view.Display;
import android.view.WindowManager;
import android.graphics.Bitmap;

import com.crashlytics.android.Crashlytics;


public class GalleryUtil {

    private GalleryUtil() { }

    public static void saveImage(DataProtos.Event event)
    {
        SavedImage si;
        if(event.getPixelsCount() > 0) {
            si = new SavedImage(event.getPixelsList(), event.getTimestamp());
        } else if(event.hasByteBlock()) {
            si = new SavedImage(event.getByteBlock(), event.getTimestamp());
        } else {
            return;
        }

        while (getListOfImages().size() > MAX_NUM_IMAGES)
        {
            File fileToDelete = null;
            ArrayList<String> imageList = getListOfImages();

            // decrement so that this stays the same after entering the loop
            LayoutGallery.sGalleryCount--;

            // sort by num_pix
            while(fileToDelete == null) {
                LayoutGallery.sGalleryCount++;
                if(si.num_pix < LayoutGallery.sGalleryCount) return;
                for(String filename : imageList) {
                    SavedImage old = new SavedImage(filename);
                    if(old.num_pix <= LayoutGallery.sGalleryCount) {
                        fileToDelete = new File(filename);
                        break;
                    }
                }
            }

            // delete image with least pixels
            CFLog.d("saveImage: deleting "+imageList.get(0));

            File file = new File(imageList.get(0));
            file.delete();
        }

        CFLog.d(" GalleryUtil::saveImage si="+si);
        try {
            File sdCard = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File myDir = new File(sdCard.getAbsolutePath() + DIRNAME);
            myDir.mkdirs();
            File file = new File(myDir, si.filename);
            FileOutputStream out = new FileOutputStream(file);
            si.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            CFLog.d(" File created: " + si.filename);
        }
            catch (Exception e) {
                Crashlytics.logException(e);
                e.printStackTrace();
        }

    }


    private static final int MAX_NUM_IMAGES = 50;

    // SD card image directory
    private static final String DIRNAME = "/.crayfis";

    // supported file formats
    private static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg",
            "png");

    public static int deleteImages() {

        int num_deleted=0;

        try {

            // Check for count
            for (String image: getListOfImages()) {

                // get file path
                File file = new File(image);
                boolean res = file.delete();
                CFLog.d("Gallery: success? " + res + " deleting file " + image);
                if (res) num_deleted++;

            }
        } catch (Exception e) {
            Crashlytics.logException(e);
        }
        return num_deleted;
    }

    private static ArrayList<String> getListOfImages() {
        ArrayList<String> filePaths = new ArrayList<String>();
        File sdCard = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File directory = new File(sdCard.getAbsolutePath()+DIRNAME);


        // check for directory
        if (directory.isDirectory()) {

            // loop through all files
            for (File f: directory.listFiles()) {

                // get file path
                String filePath = f.getAbsolutePath();
                // CFLog.d("Gallery: Gallery file "+i+" = "+filePath);

                // check for supported file extension
                if (IsSupportedFile(filePath)) {
                    // Add image path to array list
                    //CFLog.d("Gallery: Adding file "+i+" = "+filePath);
                     filePaths.add(filePath);
                }
            }
        }
        return filePaths;
    }


    // Reading file paths from SDCard
    public static ArrayList<SavedImage> getSavedImages() {
        ArrayList<SavedImage> filePaths = new ArrayList<SavedImage>();

        for (String filePath: getListOfImages()) {

            try {
                filePaths.add(new SavedImage(filePath));
            } catch (OutOfMemoryError e) {
                File deleteFile = new File(filePath);
                deleteFile.delete();
            } catch (Exception e) {
                Crashlytics.logException(e);
                // couldn't do it. Don't crash.
            }

        }
        return filePaths;
    }

    // Check supported file extensions
    private static boolean IsSupportedFile(String filePath) {
        String ext = filePath.substring((filePath.lastIndexOf(".") + 1),
                filePath.length());

        return FILE_EXTN.contains(ext.toLowerCase(Locale.getDefault()));

    }

    /*
     * getting screen width
     */
    public static int getScreenWidth(Context context) {
        int columnWidth;
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        final Point point = new Point();
        try {
            display.getSize(point);
        } catch (java.lang.NoSuchMethodError ignore) { // Older device
            point.x = display.getWidth();
            point.y = display.getHeight();
        }
        columnWidth = point.x;
        return columnWidth;
    }


}
