package edu.uci.crayfis;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

/**
 * Created by danielwhiteson on 11/18/14.
 */


import android.content.Context;
        import android.os.Bundle;
        import android.support.v4.app.Fragment;
        import android.view.LayoutInflater;
        import android.view.View;
        import android.view.ViewGroup;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LineGraphView;

import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;

import com.jjoe64.graphview.GraphViewDataInterface;
import com.jjoe64.graphview.ValueDependentColor;

import edu.uci.crayfis.particle.ParticleReco;

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

    private static ParticleReco mParticleReco;

    private static LayoutTime mInstance =null;


    private static Context _context;

    private static GraphView mGraphTime;
    private static GraphViewSeries mGraphSeriesTime;
    private GraphViewSeries.GraphViewSeriesStyle mGraphSeriesStyleTime;

    public static void updateData() {

        if (mParticleReco !=null)
           mGraphSeriesTime.resetData(make_graph_data(mParticleReco.hist_max.values, false, mParticleReco.hist_max.current_time, mParticleReco.hist_max.values.length));
    }





    private LayoutTime(Context context)
    {
        _context = context;
        mParticleReco = ParticleReco.getInstance();
    }

    public static Fragment getInstance(Context context) {
        if (mInstance==null)
            mInstance= new LayoutTime(context);

        return mInstance;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.time, null);

        int novals[] = new int[256];
        for (int i=0;i<256;i++) novals[i]=1;

        mGraphTime = new LineGraphView (_context, "");
        mGraphTime.setManualYAxisBounds(30., 0.);
        mGraphTime.setHorizontalLabels(new String[] {"","Time"," "," "});
        GraphViewSeriesStyle mGraphSeriesStyleTime = new GraphViewSeriesStyle();
        mGraphSeriesStyleTime.setValueDependentColor(new ValueDependentColorY());
        mGraphSeriesTime = new GraphViewSeries("aaa",mGraphSeriesStyleTime,make_graph_data(novals, true, 0, 20));
        mGraphTime.setScalable(true);
        mGraphTime.addSeries(mGraphSeriesTime);

        root.addView(mGraphTime);

        return root;
    }

}