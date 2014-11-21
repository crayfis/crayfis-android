package edu.uci.crayfis.gallery;

/**
 * Created by danielwhiteson on 11/19/14.
 */


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import android.util.Log;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Point;
import android.os.Environment;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

public class Utils {
    // Number of columns of Grid View
    public static final int NUM_OF_COLUMNS = 3;

    // Gridview image padding
    public static final int GRID_PADDING = 8; // in dp

    // SD card image directory
    public static final String DIRNAME = "/crayfis";

    // supported file formats
    public static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg",
            "png");

    private Context _context;

    // constructor
    public Utils(Context context) {
        this._context = context;
    }

    // Reading file paths from SDCard
    public ArrayList<String> getFilePaths() {
        ArrayList<String> filePaths = new ArrayList<String>();

        File sdCard = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File directory = new File(sdCard.getAbsolutePath()+DIRNAME);


        // check for directory
        if (directory.isDirectory()) {
            // getting list of file paths
            File[] listFiles = directory.listFiles();
            Log.d("Gallery"," Gallery files num="+listFiles.length);
            // Check for count
            if (listFiles.length > 0) {

                // loop through all files
                for (int i = 0; i < listFiles.length; i++) {

                    // get file path
                    String filePath = listFiles[i].getAbsolutePath();
                    Log.d("Gallery"," Gallery file "+i+" = "+filePath);

                    // check for supported file extension
                    if (IsSupportedFile(filePath)) {
                        // Add image path to array list
                        Log.d("Gallery"," Adding file "+i+" = "+filePath);

                        filePaths.add(filePath);
                    }
                }
            } else {
                // image directory is empty
                Toast.makeText(
                        _context,
                        DIRNAME
                                + " is empty. Please load some images in it !",
                        Toast.LENGTH_LONG).show();
            }

        }
        return filePaths;
    }

    // Check supported file extensions
    private boolean IsSupportedFile(String filePath) {
        String ext = filePath.substring((filePath.lastIndexOf(".") + 1),
                filePath.length());

        if (FILE_EXTN
                .contains(ext.toLowerCase(Locale.getDefault())))
            return true;
        else
            return false;

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
