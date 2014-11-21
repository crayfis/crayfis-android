package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/19/14.
 */

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridView;
import android.util.TypedValue;
import android.content.res.Resources;

import android.util.Log;
import android.widget.Toast;
import android.view.ViewGroup;
import java.util.ArrayList;
import edu.uci.crayfis.gallery.Utils;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import edu.uci.crayfis.gallery.GridViewImageAdapter;


public class LayoutGallery extends Fragment {

    private Utils utils;
    private ArrayList<String> imagePaths = new ArrayList<String>();
    private GridViewImageAdapter adapter;
    private GridView gridView;
    private int columnWidth;

    private static LayoutGallery mInstance =null;
    public static Context _context;


    public LayoutGallery(Context context)
    {
        _context = context;
    }

    public static Fragment getInstance(Context context) {
        if (mInstance==null)
            mInstance = new LayoutGallery(context);


        return mInstance;
    }

    private void InitilizeGridLayout() {
        Resources r = getResources();
        float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                Utils.GRID_PADDING, r.getDisplayMetrics());

        columnWidth = (int) ((utils.getScreenWidth() - ((Utils.NUM_OF_COLUMNS + 1) * padding)) / Utils.NUM_OF_COLUMNS);

        gridView.setNumColumns(Utils.NUM_OF_COLUMNS);
        gridView.setColumnWidth(columnWidth);
        gridView.setStretchMode(GridView.NO_STRETCH);
        gridView.setPadding((int) padding, (int) padding, (int) padding,
                (int) padding);
        gridView.setHorizontalSpacing((int) padding);
        gridView.setVerticalSpacing((int) padding);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.gallery, null);

        gridView = (GridView) root.findViewById(R.id.grid_view);

        utils = new Utils(_context);

        // Initilizing Grid View
        InitilizeGridLayout();

        // loading all image paths from SD card
        imagePaths = utils.getFilePaths();

        // Gridview adapter
        adapter = new GridViewImageAdapter(LayoutGallery.this, imagePaths,columnWidth);

        // setting grid view adapter
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Log.d("GridView"," clicked"+position+id);
                Toast.makeText(_context, "img" + position, Toast.LENGTH_SHORT).show();
            }
        });

        return root;

    }

}
