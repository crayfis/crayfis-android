package io.crayfis.android.ui.navdrawer.status;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.crayfis.android.R;
import io.crayfis.android.util.ViewUtil;

/**
 * Show the user the status of the application.
 */
final class DataCollectionStatsView extends LinearLayout {

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
     * Set the {@link DataCollectionStatsView.Status} for this view.
     *
     * @param status The status.
     */
    void setStatus(@NonNull final Status status) {
        mFramesScanned.setText(ViewUtil.formatDecimalScientific(status.getTotalFrames()));
        mPixelsScanned.setText(ViewUtil.formatDecimalScientific(status.getTotalPixels()));
        mCandidates.setText(ViewUtil.formatDecimal(status.getTotalEvents()));
    }

    /**
     * General status values for the application.  Create a new instance through
     * {@link DataCollectionStatsView.Status.Builder}.
     */
    static final class Status {

        private final long mTotalFrames;
        private final long mTotalEvents;
        private final long mTotalPixels;

        private Status(final long totalEvents, final long totalPixels, final long totalFrames) {
            mTotalFrames = totalFrames;
            mTotalEvents = totalEvents;
            mTotalPixels = totalPixels;
        }

        /**
         * Get the total events.
         *
         * @return The total events.
         */
        long getTotalEvents() {
            return mTotalEvents;
        }
        long getTotalFrames() {
            return mTotalFrames;
        }

        /**
         * Get the total candidate pixels.
         *
         * @return The total candidate pixels.
         */
        long getTotalPixels() {
            return mTotalPixels;
        }

        /**
         * Builder for {@link DataCollectionStatsView.Status}.
         */
        public static final class Builder {

            private long mTotalEvents;
            private long mTotalPixels;
            private long mTotalFrames;



            /**
             * Set the total events.
             *
             * @param totalEvents The total events.
             * @return This instance for chaining.
             */
            Builder setTotalEvents(final long totalEvents) {
                mTotalEvents = totalEvents;
                return this;
            }

            Builder setTotalFrames(final long totalFrames) {
                mTotalFrames = totalFrames;
                return this;
            }

            /**
             * Set the total candidate pixels.
             *
             * @param totalPixels The total candidate pixels.
             * @return This instance for chaining.
             */
            Builder setTotalPixels(final long totalPixels) {
                mTotalPixels = totalPixels;
                return this;
            }



            /**
             * Create a new instance of {@link DataCollectionStatsView.Status}.
             * @return A new instance of {@link DataCollectionStatsView.Status}.
             */
            Status build() {
                return new Status(
                        mTotalEvents, mTotalPixels,mTotalFrames);
            }
        }
    }
}
