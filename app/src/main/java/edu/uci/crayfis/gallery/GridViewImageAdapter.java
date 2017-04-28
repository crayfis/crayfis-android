package edu.uci.crayfis.gallery;

/**
 * Created by danielwhiteson on 11/19/14.
 */

    import java.io.File;
    import java.io.FileInputStream;
    import java.io.FileNotFoundException;
    import java.util.ArrayList;


    import android.content.Context;

    import android.graphics.Bitmap;
    import android.graphics.BitmapFactory;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.BaseAdapter;
    import android.widget.GridView;
    import android.widget.ImageView;

    import com.crashlytics.android.Crashlytics;

    import edu.uci.crayfis.ui.LayoutGallery;


public class GridViewImageAdapter extends BaseAdapter {

        private Context _context;
        private LayoutGallery _activity;
        private ArrayList<SavedImage> _filePaths = new ArrayList<SavedImage>();
        private int imageWidth;

        public GridViewImageAdapter(Context context, ArrayList<SavedImage> filePaths,
                                    int imageWidth) {
            this._filePaths = filePaths;
            this.imageWidth = imageWidth;
            this._context = context;
        }

        @Override
        public int getCount() {
            return this._filePaths.size();
        }

        @Override
        public Object getItem(int position) {
            return this._filePaths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {



            ImageView imageView;
            if (convertView == null) {
                imageView = new ImageView(_context);
            } else {
                imageView = (ImageView) convertView;
            }

            // get screen dimensions
            try {
                Bitmap image = decodeFile(_filePaths.get(position).filename, imageWidth,
                        imageWidth);

                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setLayoutParams(new GridView.LayoutParams(imageWidth,
                        imageWidth));
                imageView.setImageBitmap(image);
            } catch (Exception e ) {              Crashlytics.logException(e);
                e.printStackTrace();
            }

            return imageView;

        }



        /*
         * Resizing image size
         */
        public static Bitmap decodeFile(String filePath, int WIDTH, int HIGHT) {
            try {

                File f = new File(filePath);

                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(new FileInputStream(f), null, o);

                final int REQUIRED_WIDTH = WIDTH;
                final int REQUIRED_HIGHT = HIGHT;
                int scale = 1;
                while (o.outWidth / scale / 2 >= REQUIRED_WIDTH
                        && o.outHeight / scale / 2 >= REQUIRED_HIGHT)
                    scale *= 2;

                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = scale;
                return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
            } catch (FileNotFoundException e) {
                Crashlytics.logException(e);
                e.printStackTrace();
            } catch (OutOfMemoryError e){ Crashlytics.logException(e); /* don't want to do anything if OOM */ }
            return null;
        }

    }




