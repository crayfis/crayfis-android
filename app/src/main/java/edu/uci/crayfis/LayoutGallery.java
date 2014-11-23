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
import android.widget.TextView;
import android.widget.Button;

import android.util.TypedValue;
import android.content.res.Resources;

import edu.uci.crayfis.util.CFLog;
import android.widget.Toast;
import android.view.ViewGroup;
import java.util.ArrayList;
import edu.uci.crayfis.gallery.Utils;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import edu.uci.crayfis.gallery.GridViewImageAdapter;

import edu.uci.crayfis.gallery.SavedImage;

public class LayoutGallery extends Fragment {

    private Utils utils;
//    private ArrayList<String> imagePaths = new ArrayList<String>();
    private ArrayList<SavedImage> images = new ArrayList<SavedImage>();
    private GridViewImageAdapter adapter;
    private GridView gridView;
    private TextView textView;

    public static Context _context;

    private int columnWidth;

    private static LayoutGallery mInstance =null;


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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.gallery, null);

        gridView = (GridView) root.findViewById(R.id.grid_view);
        textView = (TextView) root.findViewById(R.id.text_gallery);

        Context context = getActivity();
        utils = new Utils(context);

        // Initilizing Grid View
        InitilizeGridLayout();

        // loading all image paths from SD card
        images = utils.getSavedImages();

        textView.setText(images.size()+" Images");

        final Button button2 = (Button)root.findViewById(R.id.delete_gallery);
        button2.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                CFLog.d("Layout Gallery: deleting images!");
                utils.deleteImages();
                images = utils.getSavedImages();

            }
        });

        // Gridview adapter
        adapter = new GridViewImageAdapter(getActivity(), images,columnWidth);

        // setting grid view adapter
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                CFLog.d("GridView: clicked"+position+id);
                Toast.makeText(getActivity(), "Pixel hits: " + images.get(position).num_pix+"  Max pixel: "+images.get(position).max_pix+"  Date: "+images.get(position).date, Toast.LENGTH_SHORT).show();
            }
        });

        return root;

    }

}
