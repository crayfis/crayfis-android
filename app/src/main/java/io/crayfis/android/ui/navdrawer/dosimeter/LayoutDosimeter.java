package io.crayfis.android.ui.navdrawer.dosimeter;

import io.crayfis.android.server.CFConfig;
import io.crayfis.android.R;
import io.crayfis.android.ui.navdrawer.NavDrawerFragment;

import io.crayfis.android.trigger.L1.L1Calibrator;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * Created by danielwhiteson on 11/18/14.
 */


import android.widget.Toast;

import com.jjoe64.graphview.GraphView;

import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


public class LayoutDosimeter extends NavDrawerFragment {

    private final CFConfig CONFIG = CFConfig.getInstance();

    private static final @StringRes int ABOUT_ID = R.string.toast_dosimeter;

    public static DataPoint[] make_graph_data(Integer values[])
    {
        //CFLog.i(" Making graph data for nbins ="+values.length);
        int max_bin = values.length;

        boolean do_log=false;
        DataPoint gd[] = new DataPoint[max_bin];
        for (int i=0;i<max_bin;i++)
        {
            //CFLog.i(" make graph data: "+i);
            if (do_log) {
                if (values[i] > 0)
                    gd[i] = new DataPoint(i, java.lang.Math.log(values[i]));
                else
                    gd[i] = new DataPoint(i, 0);
            } else
                gd[i] = new DataPoint(i, values[i]);
        }
        return gd;
    }

    private SpeedometerView mSpeedometerView;

    private boolean shown_message=false;

    private GraphView mGraphTime;
    private LineGraphSeries<DataPoint> mGraphSeriesTime;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            if (!shown_message)
            {

                Toast.makeText(getActivity(), R.string.toast_dosimeter,
                        Toast.LENGTH_SHORT).show();
            shown_message=true;
            }


        }
        super.setUserVisibleHint(isVisibleToUser);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.dosimeter, container, false);

        //Integer novals[] = new Integer[256];
        //for (int i=0;i<256;i++) novals[i]=1;

        mGraphTime = (GraphView) root.findViewById(R.id.time_graph_view);
        Viewport viewport = mGraphTime.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMaxY(30);
        viewport.setMinY(0);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(1000);
        viewport.setMinX(0);
        viewport.setScalable(false);
        viewport.setScrollable(false);

        GridLabelRenderer gridLabelRenderer = mGraphTime.getGridLabelRenderer();
        gridLabelRenderer.setNumVerticalLabels(4);
        gridLabelRenderer.setHorizontalLabelsColor(Color.WHITE);
        gridLabelRenderer.setVerticalLabelsColor(Color.WHITE);
        gridLabelRenderer.setHorizontalAxisTitle(getString(R.string.dosimeter_xlabel));
        gridLabelRenderer.setHorizontalLabelsVisible(false);

        mGraphSeriesTime = new LineGraphSeries<>();
        mGraphSeriesTime.setColor(Color.BLUE);

        mGraphTime.addSeries(mGraphSeriesTime);

        mSpeedometerView = (SpeedometerView) root.findViewById(R.id.needle_view);



        mSpeedometerView.setLabelConverter(new SpeedometerView.LabelConverter() {
            @Override
            public String getLabelFor(double progress, double maxProgress) {
                return String.valueOf((int) Math.round(progress));
            }
        });

        // configure value range and ticks
        mSpeedometerView.setMaxSpeed(30);
        mSpeedometerView.setMajorTickStep(5);
        mSpeedometerView.setMinorTicks(1);

        // Configure value range colors
        mSpeedometerView.addColoredRange(0, 5, Color.GREEN);
        mSpeedometerView.addColoredRange(5, 20, Color.YELLOW);
        mSpeedometerView.addColoredRange(20, 30, Color.RED);

        return root;
    }

    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

    @Override
    public void update() {

        L1Calibrator cal = L1Calibrator.getInstance();
        if (mGraphSeriesTime !=null) {
            Integer[] values = cal.getMaxPixels();
            mGraphSeriesTime.resetData(make_graph_data(values));
            // dosimeter average
            float mean = 0;
            for(int val : values)
                mean += val;
            mean /= (1.0*values.length);
            mSpeedometerView.setSpeed(mean);
        }
    }

}