package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.jjoe64.graphview.BarGraphView;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewDataInterface;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.GraphViewStyle;
import com.jjoe64.graphview.ValueDependentColor;

import edu.uci.crayfis.particle.ParticleReco;

public class LayoutHist extends Fragment{




    private class ValueDependentColorX implements ValueDependentColor
    {
        @Override
        public int get (GraphViewDataInterface data){
            if (data.getY() == 0) return Color.BLACK;

            if (data.getX() == 0)
                return Color.GREEN;
            if (data.getX() == 1)
            return Color.BLUE;
            return Color.RED;

        }
    }

    public static GraphView.GraphViewData[] make_graph_data(int values[])
    {

        final CFConfig CONFIG = CFConfig.getInstance();
        int bins[] = {0,0,0};
        GraphView.GraphViewData gd[] = new GraphView.GraphViewData[3];

        // divide into 3 bins
        for (int i=0;i<values.length;i++) {
            if (i >= CONFIG.getL2Threshold()) {
                if (i < 2 * CONFIG.getL2Threshold())
                    bins[0] += values[i];
                else if (i < 4 * CONFIG.getL2Threshold()) {
                    bins[1] += values[i];
                } else {
                    bins[2] += values[i];
                }
            }
        }

        // initialize GV data
        for (int i=0;i<3;i++)
        {
            /* if (bins[i]>0)
                gd[i] = new GraphView.GraphViewData(i, java.lang.Math.log(bins[i]));
            else */

            gd[i] = new GraphView.GraphViewData(i, bins[i]);
        }
        return gd;
    }

    // class to find particles in frames
    private static ParticleReco mParticleReco;

    private static GraphView mGraph;


    private static GraphViewSeries mGraphSeries;

    public static void updateData() {

        if (mParticleReco !=null) {
            if (mGraphSeries !=null && mParticleReco.h_l2pixel != null)
                mGraphSeries.resetData(make_graph_data(mParticleReco.h_l2pixel.getValues()));
            if (mGraph != null && mParticleReco.h_l2pixel != null)
                mGraph.setManualYAxisBounds(java.lang.Math.max(100.,1.2*mParticleReco.h_l2pixel.getIntegral()), 0.);
        }

    }

    private static LayoutHist mInstance =null;


    public LayoutHist()
    {
        mParticleReco = ParticleReco.getInstance();
    }

    public static LayoutHist getInstance() {
        if (mInstance==null)
            mInstance= new LayoutHist();

        return mInstance;
    }

    private static boolean shown_message=false;


    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Context act = getActivity();
            if (mParticleReco != null && act !=null)
            {
                if (mParticleReco.h_l2pixel.getIntegral()==0)
                {
                    Toast.makeText(act, R.string.hist_toast_zero,Toast.LENGTH_LONG).show();
                } else {
                    if (!shown_message)
                    Toast.makeText(act, R.string.hist_toast,Toast.LENGTH_LONG).show();

                }
                shown_message=true;
            }


        }
        else {  }
        super.setUserVisibleHint(isVisibleToUser);
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        mGraph = new BarGraphView(container.getContext(),(String) null);

        final ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(container.getLayoutParams());
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        final Resources resources = getResources();
        mGraph.setLayoutParams(params);
        final int padding = resources.getDimensionPixelSize(R.dimen.standard_margin);
        mGraph.setPadding(padding, padding, padding, padding);

        mGraph.setManualYAxisBounds(100, 0);
        mGraph.setHorizontalLabels( getResources().getStringArray(R.array.hist_bins));

        final GraphViewStyle graphViewStyle = mGraph.getGraphViewStyle();
        graphViewStyle.setHorizontalLabelsColor(resources.getColor(R.color.palette_white));
        graphViewStyle.setVerticalLabelsColor(resources.getColor(R.color.palette_white));
        graphViewStyle.setTextSize(resources.getDimensionPixelSize(R.dimen.hist_text_size));

        GraphViewSeriesStyle mGraphSeriesStyle = new GraphViewSeriesStyle();
        mGraphSeriesStyle.setValueDependentColor(new ValueDependentColorX());
        mGraphSeries = new GraphViewSeries("aaa",mGraphSeriesStyle    ,make_graph_data(new int[256]));
        mGraph.setScalable(false);
        mGraph.addSeries(mGraphSeries);

        return mGraph;
    }

}