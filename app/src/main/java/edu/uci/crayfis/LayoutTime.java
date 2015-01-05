package edu.uci.crayfis;

import edu.uci.crayfis.SpeedometerView;

import edu.uci.crayfis.calibration.L1Calibrator;
import edu.uci.crayfis.util.CFLog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * Created by danielwhiteson on 11/18/14.
 */


import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LineGraphView;

import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;

import com.jjoe64.graphview.GraphViewDataInterface;
import com.jjoe64.graphview.ValueDependentColor;


public class LayoutTime extends Fragment {

    private final CFConfig CONFIG = CFConfig.getInstance();


    private class ValueDependentColorY implements ValueDependentColor
    {
        @Override
        public int get (GraphViewDataInterface data){
            if (data.getY() == 0) return Color.BLACK;
            if (data.getY() < CONFIG.getL2Threshold())
                return Color.BLUE;
            return Color.RED;
        }
    }

    public static GraphView.GraphViewData[] make_graph_data(Integer values[])
    {
        //CFLog.i(" Making graph data for nbins ="+values.length);
        int max_bin = values.length;

        boolean do_log=false;
        GraphView.GraphViewData gd[] = new GraphView.GraphViewData[max_bin];
        for (int i=0;i<max_bin;i++)
        {
            //CFLog.i(" make graph data: "+i);
            if (do_log) {
                if (values[i] > 0)
                    gd[i] = new GraphView.GraphViewData(i, java.lang.Math.log(values[i]));
                else
                    gd[i] = new GraphView.GraphViewData(i, 0);
            } else
                gd[i] = new GraphView.GraphViewData(i, values[i]);
        }
        return gd;
    }

    private static L1Calibrator mL1Calibrator;
    private static LayoutTime mInstance =null;

    private static SpeedometerView mSpeedometerView;

    private static boolean shown_message=false;

    private static GraphView mGraphTime;
    private static GraphViewSeries mGraphSeriesTime;
    private GraphViewSeries.GraphViewSeriesStyle mGraphSeriesStyleTime;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            if (!shown_message)
            {

                Toast.makeText(getActivity(), "This pane shows a radiation dosimeter. Still in development.",
                        Toast.LENGTH_SHORT).show();
            shown_message=true;
            }


        }
        else {  }
        super.setUserVisibleHint(isVisibleToUser);
    }

    public static void updateData() {

        if (mL1Calibrator !=null && mGraphSeriesTime !=null) {
            Integer[] values = new Integer[mL1Calibrator.getMaxPixels().size()];
            values=mL1Calibrator.getMaxPixels().toArray(values);
            mGraphSeriesTime.resetData(make_graph_data(values));
            // time average
            float mean = 0;
            for (int i=0;i<values.length;i++)
                mean += values[i];
            mean /= (1.0*values.length);
            mSpeedometerView.setSpeed(mean);
        }
    }

    public LayoutTime()
    {
        mL1Calibrator = L1Calibrator.getInstance();
    }

    public static LayoutTime getInstance() {
        if (mInstance==null)
            mInstance= new LayoutTime();

        return mInstance;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.time, null);



        Integer novals[] = new Integer[256];
        for (int i=0;i<256;i++) novals[i]=1;

        Context context = getActivity();
        mGraphTime = new LineGraphView (context, "");
        mGraphTime.setManualYAxisBounds(30., 0.);
        mGraphTime.getGraphViewStyle().setNumVerticalLabels(4);
        mGraphTime.setHorizontalLabels(new String[] {"","Frame samples",""});
        mGraphTime.getGraphViewStyle().setHorizontalLabelsColor(Color.WHITE);
        mGraphTime.getGraphViewStyle().setVerticalLabelsColor(Color.WHITE);

        GraphViewSeriesStyle mGraphSeriesStyleTime = new GraphViewSeriesStyle();
        mGraphSeriesStyleTime.setValueDependentColor(new ValueDependentColorY());
        mGraphSeriesTime = new GraphViewSeries("aaa",mGraphSeriesStyleTime,make_graph_data(novals));
        mGraphTime.setScalable(true);
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

        root.addView(mGraphTime);

        return root;
    }

}