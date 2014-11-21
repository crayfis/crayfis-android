package edu.uci.crayfis.gallery;

/**
 * Created by danielwhiteson on 11/19/14.
 */

    import edu.uci.crayfis.R;
    import java.io.File;
    import java.io.FileInputStream;
    import java.io.FileNotFoundException;
    import java.util.ArrayList;
    import android.widget.TextView;


    import android.view.LayoutInflater;
    import android.content.Context;

    import edu.uci.crayfis.util.CFLog;
    import android.app.Activity;
    import android.content.Intent;
    import android.graphics.Bitmap;
    import android.graphics.BitmapFactory;
    import android.view.View;
    import android.view.View.OnClickListener;
    import android.view.ViewGroup;
    import android.widget.BaseAdapter;
    import android.widget.GridView;
    import android.widget.ImageView;
    import edu.uci.crayfis.LayoutGallery;


    public class GridViewImageAdapter extends BaseAdapter {

        private LayoutGallery _activity;
        private ArrayList<String> _filePaths = new ArrayList<String>();
        private int imageWidth;
        private Context mContext;
        private LayoutInflater mLayoutInflater;

        public GridViewImageAdapter(LayoutGallery activity, ArrayList<String> filePaths,
                                    int imageWidth) {
            this._activity = activity;
            this._filePaths = filePaths;
            this.imageWidth = imageWidth;
            this.mContext = activity._context;
            this.mLayoutInflater = LayoutInflater.from(this.mContext);

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

            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.grid_single, parent, false);
            }

                TextView textView = (TextView) convertView.findViewById(R.id.grid_text);
                ImageView imageView = (ImageView)convertView.findViewById(R.id.grid_image);
                textView.setText(_filePaths.get(position));

                // get screen dimensions
                Bitmap image = decodeFile(_filePaths.get(position), imageWidth,
                        imageWidth);

                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                //imageView.setLayoutParams(new ViewGroup.MarginLayoutParams(
                  //      ViewGroup.MarginLayoutParams.MATCH_PARENT, ViewGroup.MarginLayoutParams.MATCH_PARENT));


                imageView.setImageBitmap(image);



            return convertView;


            /*

            ImageView imageView;
            if (convertView == null) {
                imageView = new ImageView(_activity._context);
            } else {
                imageView = (ImageView) convertView;
            }

            // get screen dimensions
            Bitmap image = decodeFile(_filePaths.get(position), imageWidth,
                    imageWidth);

            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setLayoutParams(new GridView.LayoutParams(imageWidth,
                    imageWidth));
            imageView.setImageBitmap(image);

            // image view click listener
            imageView.setOnClickListener(new OnImageClickListener(position));







            return imageView;
            */
        }

        class OnImageClickListener implements OnClickListener {

            int _postion;

            // constructor
            public OnImageClickListener(int position) {
                this._postion = position;
            }


            @Override
            public void onClick(View v) {
                // on selecting grid view image
                // launch full screen activity
                /*
                Intent i = new Intent(_activity, FullScreenViewActivity.class);
                i.putExtra("position", _postion);
                _activity.startActivity(i);
                */
            }


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
                e.printStackTrace();
            }
            return null;
        }

    }




