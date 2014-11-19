package edu.uci.crayfis;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import edu.uci.crayfis.particle.ParticleReco;

import com.jjoe64.graphview.BarGraphView;
import com.jjoe64.graphview.LineGraphView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewDataInterface;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.ValueDependentColor;

import edu.uci.crayfis.widget.DataView;

public class LayoutHist extends Fragment{

    private final CFConfig CONFIG = CFConfig.getInstance();

    public static DataView mDataView;

    public LayoutHist() {}

    private class ValueDependentColorX implements ValueDependentColor
    {
        @Override
        public int get (GraphViewDataInterface data){
            if (data.getY() == 0) return Color.BLACK;
            if (data.getX() < CONFIG.getL2Threshold())
                return Color.BLUE;
            return Color.RED;
        }
    }

    public static GraphView.GraphViewData[] make_graph_data(int values[], boolean do_log, int start, int max_bin)
    {
        // show some empty bins
        if (max_bin<values.length)
            max_bin += 2;

        GraphView.GraphViewData gd[] = new GraphView.GraphViewData[max_bin];
        int which=start+1;
        for (int i=0;i<max_bin;i++)
        {
            if (which>=max_bin){ which=0;}
            if (do_log) {
                if (values[which] > 0)
                    gd[i] = new GraphView.GraphViewData(i, java.lang.Math.log(values[which]));
                else
                    gd[i] = new GraphView.GraphViewData(i, 0);
            } else
                gd[i] = new GraphView.GraphViewData(i, values[which]);
            which++;


        }
        return gd;
    }

    // class to find particles in frames
    private static ParticleReco mParticleReco;

    private static GraphView mGraph;


    private static GraphViewSeries mGraphSeries;

    private GraphViewSeriesStyle mGraphSeriesStyle;

    public static void updateData() {

        if (mParticleReco !=null)
            mGraphSeries.resetData(make_graph_data(mParticleReco.h_pixel.values, true,-1,mParticleReco.h_pixel.max_bin));

    }

    private static LayoutHist mInstance =null;

    private static Context _context;

    private LayoutHist(Context context)
    {
        _context = context;
        mParticleReco = ParticleReco.getInstance();
    }

    public static Fragment getInstance(Context context) {
        if (mInstance==null)
            mInstance= new LayoutHist(context);

        return mInstance;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.hist, null);

        mDataView = (DataView) root.findViewById(R.id.data_view);


        int novals[] = new int[256];
        for (int i=0;i<256;i++) novals[i]=1;

        /// test graphing
        mGraph = new BarGraphView(_context," ");
        mGraph.setManualYAxisBounds(20., 0.);
        mGraph.setHorizontalLabels(new String[] {"","Pixel","values"});
        mGraph.setVerticalLabels(new String[] {""});

        GraphViewSeriesStyle mGraphSeriesStyle = new GraphViewSeriesStyle();
        mGraphSeriesStyle.setValueDependentColor(new ValueDependentColorX());
        mGraphSeries =     new GraphViewSeries("aaa",mGraphSeriesStyle    ,make_graph_data(novals, true, 0, 20));
        mGraph.setScalable(true);
        mGraph.addSeries(mGraphSeries);

        root.addView(mGraph);

        return root;
    }

}