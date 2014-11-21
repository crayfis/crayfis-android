package edu.uci.crayfis.widget;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.TextView;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;

/**
 * Show the user the status of the application.
 */
public final class DataView extends TextView {

    private static final CFConfig CONFIG = CFConfig.getInstance();

    public DataView(final Context context) {
        super(context);
    }

    public DataView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public DataView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Set the {@link edu.uci.crayfis.widget.DataView.Status} for this view.
     *
     * @param status The status.
     */
    public void setStatus(@NonNull final Status status) {
        final long totalFrames = status.getTotalFrames();
        final int totalEvents = status.getTotalEvents();

        final int totalCandidates = status.getTotalPixels();
                final String text = String.format("Frames scanned: %s\nFrames selected: %s\n Candidates: %s",
                        totalFrames,
                        totalEvents,
                        totalCandidates);
                setText(text);

        }



    /**
     * General status values for the application.  Create a new instance through
     * {@link edu.uci.crayfis.widget.DataView.Status.Builder}.
     */
    public static final class Status {

        private final long mTotalFrames;

        private final int mTotalEvents;
        private final int mTotalPixels;

        private Status(
                       final int totalEvents, final int totalPixels, final long totalFrames) {

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
        public int getTotalPixels() {
            return mTotalPixels;
        }

        /**
         * Builder for {@link edu.uci.crayfis.widget.DataView.Status}.
         */
        public static final class Builder {

            private int mTotalEvents;
            private int mTotalPixels;
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
            public Builder setTotalPixels(final int totalPixels) {
                mTotalPixels = totalPixels;
                return this;
            }



            /**
             * Create a new instance of {@link edu.uci.crayfis.widget.DataView.Status}.
             * @return A new instance of {@link edu.uci.crayfis.widget.DataView.Status}.
             */
            public Status build() {
                return new Status(
                        mTotalEvents, mTotalPixels,mTotalFrames);
            }
        }
    }
}
