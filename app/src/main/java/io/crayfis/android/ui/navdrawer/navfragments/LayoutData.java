package io.crayfis.android.ui.navdrawer.navfragments;

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

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import io.crayfis.android.server.CFConfig;
import io.crayfis.android.R;
import io.crayfis.android.trigger.L1.calibration.Histogram;

public class LayoutData extends CFFragment {

    private final @StringRes int ABOUT_ID = R.string.toast_hist;

    private final float GOOD_EPM = 1f;
    private final float IDEAL_EPM = 0.3f;

    private final int FAIR_COLOR = Color.GREEN;
    private final int GOOD_COLOR = Color.YELLOW;
    private final int IDEAL_COLOR = Color.RED;

    private final int PADDING = 80;
    private final int LOG_OFFSET = 1;

    private int mGoodCutoff;
    private int mIdealCutoff;

    private static final Histogram histL2Pixels = new Histogram(256);

    private final CFConfig CONFIG = CFConfig.getInstance();

    private GraphView mGraphView;
    private BarGraphSeries<DataPoint> mGraphSeries;
    private Viewport mViewport;
    private GridLabelRenderer mGridLabelRenderer;
    private int mMaxX;

    private static LayoutData mInstance =null;

    private class ValueDependentColorX implements ValueDependentColor<DataPoint>
    {
        @Override
        public int get(DataPoint data){
            if (data.getY() == 0) return Color.BLACK;

            if (data.getX() <= mGoodCutoff)
                return FAIR_COLOR;
            if (data.getX() <= mIdealCutoff)
                return GOOD_COLOR;
            return IDEAL_COLOR;

        }
    }

    private class LogLabelFormatter extends DefaultLabelFormatter {
        @Override
        public String formatLabel(double value, boolean isValueX) {

            if (isValueX) return super.formatLabel(value, true);
            value -= LOG_OFFSET;

            // check if we have an integer power
            if (Math.floor(value) == value) {
                if (value == 0) return "1";
                if (value < 0) return "";
                return "1e" + (int) value;
            } else {
                return "";
            }
        }
    }

    public static void appendData(int val) {
        histL2Pixels.fill(val);
    }

    public DataPoint[] make_graph_data() {
        long[] values = histL2Pixels.getValues();

        // include an overflow bin if necessary
        DataPoint[] data = new DataPoint[256];

        // initialize GV data
        for (int i = 0; i < 256; i++) {
            if (values[i] > 0) {
                data[i] = new DataPoint(i, LOG_OFFSET + Math.log10(values[i]));
                if(i > mMaxX) {
                    mMaxX = i;
                }
            } else {
                data[i] = new DataPoint(i, 0);
            }
        }
        return data;
    }
    


    public LayoutData() {    }

    public static LayoutData getInstance() {
        if (mInstance==null)
            mInstance= new LayoutData();

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
        mViewport.setXAxisBoundsManual(true);
        mViewport.setYAxisBoundsManual(true);
        mViewport.setMinX(0);
        mViewport.setMaxX(20);
        mViewport.setMinY(0);
        mViewport.setScalable(false);
        mViewport.setScrollable(false);

        Resources resources = rtn.getResources();

        mGridLabelRenderer = mGraphView.getGridLabelRenderer();
        mGridLabelRenderer.setTextSize(resources.getDimensionPixelSize(R.dimen.hist_text_size));
        mGridLabelRenderer.setPadding(PADDING);
        mGridLabelRenderer.setLabelFormatter(new LogLabelFormatter());

        mGraphSeries = new BarGraphSeries<>();

        mGraphSeries.setValueDependentColor(new ValueDependentColorX());

        mGraphView.addSeries(mGraphSeries);

        mGraphSeries.setSpacing(0);

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
        legendRenderer.setAlign(LegendRenderer.LegendAlign.TOP);


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

            final long totalEntries = histL2Pixels.getEntries();
            final long[] values = histL2Pixels.getValues();
            if(totalEntries == 0) return;

            final int targetGood = (int)((1-GOOD_EPM/passRate)*totalEntries+1);
            final int targetIdeal = (int)((1-IDEAL_EPM/passRate)*totalEntries+1);

            int integral = 0;
            int i=-1;

            while(integral < targetGood) {
                integral += values[++i];
            }
            mGoodCutoff = i;

            while(integral < targetIdeal) {
                integral += values[++i];
            }
            mIdealCutoff = i;

            mGraphSeries.resetData(make_graph_data());

            if(mViewport != null) {
                final double maxY = 1.2 * mGraphSeries.getHighestValueY();
                mViewport.setMaxY(maxY);
                mGridLabelRenderer.setNumVerticalLabels((int)maxY+2);
                mViewport.setMaxX((int)Math.min(256, (1.2*mMaxX)));
            }
        }

    }

}