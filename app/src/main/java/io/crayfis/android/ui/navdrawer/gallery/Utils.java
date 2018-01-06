package io.crayfis.android.ui.navdrawer.gallery;

/**
 * Created by danielwhiteson on 11/19/14.
 */


import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import io.crayfis.android.util.CFLog;

import android.content.Context;
import android.graphics.Point;
import android.os.Environment;
import android.view.Display;
import android.view.WindowManager;
import android.graphics.Bitmap;

import com.crashlytics.android.Crashlytics;




public class Utils {

    public void saveImage(SavedImage si)
    {
        ArrayList<String> imageList = getListOfImages();

        CFLog.d("saveImage: current images: "+imageList.size());

        if (imageList.size() > MAX_NUM_IMAGES)
        {
         // delete first image
            CFLog.d("saveImage: deleting "+imageList.get(0));

            File file = new File(imageList.get(0));
            file.delete();
        }

        CFLog.d(" Utils::saveImage si="+si);
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


    private static final int MAX_NUM_IMAGES = 100;

    // Number of columns of Grid View
    public static final int NUM_OF_COLUMNS = 2;

    // Gridview image padding
    public static final int GRID_PADDING = 4; // in dp

    // SD card image directory
    private static final String DIRNAME = "/.crayfis";

    // supported file formats
    private static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg",
            "png");

    private Context _context;

    // constructor
    public Utils(Context context) {
        this._context = context;
    }

    public int deleteImages() {

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

    private ArrayList<String> getListOfImages() {
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
    public ArrayList<SavedImage> getSavedImages() {
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
    private boolean IsSupportedFile(String filePath) {
        String ext = filePath.substring((filePath.lastIndexOf(".") + 1),
                filePath.length());

        return FILE_EXTN.contains(ext.toLowerCase(Locale.getDefault()));

    }

    /*
     * getting screen width
     */
    public int getScreenWidth() {
        int columnWidth;
        WindowManager wm = (WindowManager) _context
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
