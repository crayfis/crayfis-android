package io.crayfis.android.ui;

/**
 * Created by danielwhiteson on 11/18/14.
 */

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import io.crayfis.android.server.CFConfig;
import io.crayfis.android.R;
import io.crayfis.android.calibration.Histogram;

public class LayoutHist extends CFFragment {

    private final @StringRes int ABOUT_ID = R.string.toast_hist;

    private final int BIN_WIDTH = 1;
    private final float GOOD_EPM = 1f;
    private final float IDEAL_EPM = 0.3f;

    private final int FAIR_COLOR = Color.GREEN;
    private final int GOOD_COLOR = Color.YELLOW;
    private final int IDEAL_COLOR = Color.RED;

    private final int PADDING = 64;

    private int mGoodCutoff;
    private int mIdealCutoff;

    private static final Histogram histL2Pixels = new Histogram(256);

    private final CFConfig CONFIG = CFConfig.getInstance();

    private GraphView mGraphView;
    private BarGraphSeries<DataPoint> mGraphSeries;
    private Viewport mViewport;

    private static LayoutHist mInstance =null;

    private class ValueDependentColorX implements ValueDependentColor<DataPoint>
    {
        @Override
        public int get(DataPoint data){
            if (data.getY() == 0) return Color.BLACK;

            if (data.getX()*BIN_WIDTH < mGoodCutoff)
                return FAIR_COLOR;
            if (data.getX()*BIN_WIDTH < mIdealCutoff)
                return GOOD_COLOR;
            return IDEAL_COLOR;

        }
    }

    public static void appendData(int val) {
        histL2Pixels.fill(val);
    }

    public DataPoint[] make_graph_data() {
        int[] values = histL2Pixels.getValues();

        // include an overflow bin if necessary
        final int N_BINS = (int) Math.ceil(256. / BIN_WIDTH);
        DataPoint[] data = new DataPoint[N_BINS];

        int count = 0;

        // initialize GV data
        for (int i = 0; i < N_BINS; i++) {
            count += values[i];
            if ((i + 1) % BIN_WIDTH == 0 || i == 255 && count != 0) {
                data[i] = new DataPoint(i, count);
                count = 0;
            }
        }
        return data;
    }
    


    public LayoutHist() {    }

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
            if (act !=null)
            {
                if (mGraphSeries == null || mGraphSeries.isEmpty())
                {
                    Toast.makeText(act, R.string.toast_hist_zero,Toast.LENGTH_LONG).show();
                } else {
                    if (!shown_message)
                    Toast.makeText(act, R.string.toast_hist,Toast.LENGTH_LONG).show();

                }
                shown_message=true;
            }


        }
        super.setUserVisibleHint(isVisibleToUser);
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        final View rtn = inflater.inflate(R.layout.hist, container, false);
        mGraphView = (GraphView) rtn.findViewById(R.id.hist);

        mViewport = mGraphView.getViewport();
        mViewport.setYAxisBoundsManual(true);
        mViewport.setMinY(0);
        mViewport.setScalable(false);
        mViewport.setScrollable(false);
        //mGraphView.setHorizontalLabels( getResources().getStringArray(R.array.hist_bins));

        Resources resources = rtn.getResources();

        final GridLabelRenderer gridLabelRenderer = mGraphView.getGridLabelRenderer();
        gridLabelRenderer.setHorizontalLabelsColor(Color.WHITE);
        gridLabelRenderer.setVerticalLabelsColor(Color.WHITE);
        gridLabelRenderer.setTextSize(resources.getDimensionPixelSize(R.dimen.hist_text_size));
        gridLabelRenderer.setPadding(PADDING);
        //gridLabelRenderer.setHorizontalAxisTitle(getString(R.string.hist_xlabel));

        mGraphSeries = new BarGraphSeries<>();

        mGraphSeries.setValueDependentColor(new ValueDependentColorX());

        mGraphView.addSeries(mGraphSeries);

        // make a legend
        BarGraphSeries<DataPoint> graphSeriesGood = new BarGraphSeries<>();
        BarGraphSeries<DataPoint> graphSeriesIdeal = new BarGraphSeries<>();

        mGraphSeries.setTitle(getString(R.string.hist_fair));
        mGraphSeries.setColor(FAIR_COLOR);

        graphSeriesGood.setTitle(getString(R.string.hist_good));
        graphSeriesGood.setColor(GOOD_COLOR);

        graphSeriesIdeal.setTitle(getString(R.string.hist_ideal));
        graphSeriesIdeal.setColor(IDEAL_COLOR);

        mGraphView.addSeries(graphSeriesGood);
        mGraphView.addSeries(graphSeriesIdeal);

        LegendRenderer legendRenderer = mGraphView.getLegendRenderer();
        legendRenderer.setVisible(true);


        return rtn;
    }


    @Override
    public @StringRes int about() {
        return ABOUT_ID;
    }

    @Override
    public void update() {

        if (mGraphSeries !=null) {
            final double passRate = CONFIG.getTargetEventsPerMinute();

            final int totalEntries = histL2Pixels.getEntries();
            final int[] values = histL2Pixels.getValues();
            if(totalEntries == 0) return;

            final int targetGood = (int)((1-GOOD_EPM/passRate)*totalEntries+1);
            final int targetIdeal = (int)((1-IDEAL_EPM/passRate)*totalEntries+1);

            int integral = 0;
            int i=0;

            while(integral < targetGood) {
                integral += values[i++];
            }
            mGoodCutoff = i-1;

            while(integral < targetIdeal) {
                integral += values[i++];
            }
            mIdealCutoff = i-1;

            mGraphSeries.resetData(make_graph_data());

            if (mViewport != null) {
                mViewport.setMaxY(Math.max(20, 1.2 * mGraphSeries.getHighestValueY()));
            }
        }

    }

}