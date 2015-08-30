package edu.uci.crayfis.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import edu.uci.crayfis.R;
import edu.uci.crayfis.ViewUtil;

/**
 * Show the user the status of the application.
 */
public final class DataCollectionStatsView extends LinearLayout {

    private TextView mFramesScanned;
    private TextView mPixelsScanned;
    private TextView mCandidates;

    public DataCollectionStatsView(final Context context) {
        this(context, null);
    }

    public DataCollectionStatsView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DataCollectionStatsView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        inflate(context, R.layout.widget_data_collection_stats, this);
        mFramesScanned = (TextView) findViewById(R.id.frames_scanned);
        mFramesScanned.setText("0");
        mPixelsScanned = (TextView) findViewById(R.id.pixels_scanned);
        mPixelsScanned.setText("0");
        mCandidates = (TextView) findViewById(R.id.candidates);
        mCandidates.setText("0");
    }

    /**
     * Set the {@link edu.uci.crayfis.widget.DataCollectionStatsView.Status} for this view.
     *
     * @param status The status.
     */
    public void setStatus(@NonNull final Status status) {
        mFramesScanned.setText(ViewUtil.formatDecimal(status.getTotalFrames()));
        mPixelsScanned.setText(ViewUtil.formatDecimal(status.getTotalPixels()));
        mCandidates.setText(String.valueOf(status.getTotalEvents()));
    }

    /**
     * General status values for the application.  Create a new instance through
     * {@link edu.uci.crayfis.widget.DataCollectionStatsView.Status.Builder}.
     */
    public static final class Status {

        private final long mTotalFrames;
        private final int mTotalEvents;
        private final long mTotalPixels;

        private Status(final int totalEvents, final long totalPixels, final long totalFrames) {
            mTotalFrames = totalFrames;
            mTotalEvents = totalEvents;
            mTotalPixels = totalPixels;
        }

        /**
         * Get the total events.
         *
         * @return The total events.
         */
        public int getTotalEvents() {
            return mTotalEvents;
        }
        public long getTotalFrames() {
            return mTotalFrames;
        }

        /**
         * Get the total candidate pixels.
         *
         * @return The total candidate pixels.
         */
        public long getTotalPixels() {
            return mTotalPixels;
        }

        /**
         * Builder for {@link edu.uci.crayfis.widget.DataCollectionStatsView.Status}.
         */
        public static final class Builder {

            private int mTotalEvents;
            private long mTotalPixels;
            private long mTotalFrames;



            /**
             * Set the total events.
             *
             * @param totalEvents The total events.
             * @return This instance for chaining.
             */
            public Builder setTotalEvents(final int totalEvents) {
                mTotalEvents = totalEvents;
                return this;
            }

            public Builder setTotalFrames(final long totalFrames) {
                mTotalFrames = totalFrames;
                return this;
            }

            /**
             * Set the total candidate pixels.
             *
             * @param totalPixels The total candidate pixels.
             * @return This instance for chaining.
             */
            public Builder setTotalPixels(final long totalPixels) {
                mTotalPixels = totalPixels;
                return this;
            }



            /**
             * Create a new instance of {@link edu.uci.crayfis.widget.DataCollectionStatsView.Status}.
             * @return A new instance of {@link edu.uci.crayfis.widget.DataCollectionStatsView.Status}.
             */
            public Status build() {
                return new Status(
                        mTotalEvents, mTotalPixels,mTotalFrames);
            }
        }
    }
}
