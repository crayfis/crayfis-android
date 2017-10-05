package io.crayfis.android.ui;

/**
 * Created by danielwhiteson on 11/19/14.
 */

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;

import io.crayfis.android.R;
import io.crayfis.android.gallery.GridViewImageAdapter;
import io.crayfis.android.gallery.SavedImage;
import io.crayfis.android.gallery.Utils;
import io.crayfis.android.util.CFLog;

public class LayoutGallery extends CFFragment {

    private Utils utils;
//    private ArrayList<String> imagePaths = new ArrayList<String>();
    private ArrayList<SavedImage> images = new ArrayList<SavedImage>();
    private GridViewImageAdapter adapter;
    private GridView gridView;
    private TextView textView;

    public static Context _context;

    private int columnWidth;

    private static LayoutGallery mInstance =null;

    private final @StringRes int ABOUT_ID = R.string.toast_gallery;


    public LayoutGallery() {

    }

    public static LayoutGallery getInstance() {
        if (mInstance==null)
            mInstance = new LayoutGallery();

        return mInstance;
    }

    private void InitilizeGridLayout() {
        Resources r = getResources();
        float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                Utils.GRID_PADDING, r.getDisplayMetrics());

        columnWidth = (int) ((utils.getScreenWidth() - ((Utils.NUM_OF_COLUMNS + 1) * padding*2)) / Utils.NUM_OF_COLUMNS);

        gridView.setNumColumns(Utils.NUM_OF_COLUMNS);
        gridView.setColumnWidth(columnWidth);
        gridView.setStretchMode(GridView.NO_STRETCH);
        gridView.setPadding((int) padding, (int) padding, (int) padding,
                (int) padding);
        gridView.setHorizontalSpacing((int) padding);
        gridView.setVerticalSpacing((int) padding);
    }

    private static boolean shown_message=false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {

            if (images.size()==0)
            {

            Toast.makeText(getActivity(), R.string.toast_gallery_zero,Toast.LENGTH_SHORT).show();

                shown_message=true;

            }
            else {
             if (!shown_message)
             {
                 Toast.makeText(getActivity(), R.string.toast_gallery,
                         Toast.LENGTH_SHORT).show();
                 shown_message=true;
             }
            }

        }
        else {  }
        super.setUserVisibleHint(isVisibleToUser);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {

        CFLog.d("Gallery on create view called");

        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.gallery, null);

        gridView = (GridView) root.findViewById(R.id.grid_view);
        textView = (TextView) root.findViewById(R.id.text_gallery);

        Context context = getActivity();
        utils = new Utils(context);

        // Initilizing Grid View
        InitilizeGridLayout();

        // loading all image paths from SD card
        try {
            images = utils.getSavedImages();
        } catch (Exception e) {                             Crashlytics.logException(e);
        }

        textView.setText(images.size()+R.string.Images);

        final Button button2 = (Button)root.findViewById(R.id.delete_gallery);
        button2.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                CFLog.d("Layout Gallery: deleting images!");
                int num = utils.deleteImages();
                Activity act = getActivity();
                final Resources resources = getResources();
                if (act != null && !act.isFinishing()) {
                    final String msg = String.format("%s %d %s",
                            resources.getString(R.string.Deleted),
                            num,
                            resources.getString(R.string.small_images));
                    Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                }
                //images = utils.getSavedImages();

            }
        });

        // Gridview adapter
        adapter = new GridViewImageAdapter(getActivity(), images,columnWidth);

        // setting grid view adapter
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                CFLog.d("GridView: clicked"+position+id);
                try {
                    Toast.makeText(getActivity(), R.string.pixel_hits + images.get(position).num_pix + " "+ R.string.max_pixel  + images.get(position).max_pix + " " + R.string.date + images.get(position).date, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {                             Crashlytics.logException(e);
                };
            }
        });

        return root;

    }

    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

}
