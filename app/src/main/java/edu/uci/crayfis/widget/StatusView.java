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
public final class StatusView extends TextView {

    private static final CFConfig CONFIG = CFConfig.getInstance();

    public StatusView(final Context context) {
        super(context);
    }

    public StatusView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Set the {@link edu.uci.crayfis.widget.StatusView.Status} for this view.
     *
     * @param status The status.
     */
    public void setStatus(@NonNull final Status status) {
        final CFApplication application = (CFApplication) getContext().getApplicationContext();
        final CFApplication.State currentState = application.getApplicationState();

        switch(currentState) {
            case CALIBRATION: {
                final String text = String.format("Frame rate: %d fps",
                        status.getFps());
                setText(text);

                break;
            }
            case STABILIZATION: {
                setText("");
                break;
            }
            case DATA: {
                // TODO Format status.getTime() to represent hours, minutes and seconds.
                 String text = String.format("Run Time: %ds\nFrame rate: %d fps",
                        status.getTime(),
                        status.getFps()
                        );
                if (CONFIG.getAccountName() != null) {
                 text += String.format("\n\n Account: %s\n Score: %1.1f\n",CONFIG.getAccountName(),CONFIG.getAccountScore());
                }
                setText(text);
                break;
            }
            default:
                setText(currentState.toString());
        }
    }

    private String getPercentageValue(@NonNull final CFApplication.State state, final float max,
                                      final float value, final int fps) {
        final String rtn;

        if (max == 0 || value == 0) {
            CFLog.e("Passing a 0 to StatusView.getPercentageValue(): max=" + max + ", value=" + value);
            rtn = state.toString();
        } else {
            rtn = String.format("Frame rate: %d fps",
                    fps);
        }

        return rtn;
    }

    /**
     * General status values for the application.  Create a new instance through
     * {@link edu.uci.crayfis.widget.StatusView.Status.Builder}.
     */
    public static final class Status {
        private final int mEventCount;
        private final int mStabilizationCount;
        private final int mFps;
        private final int mTotalEvents;
        private final int mTotalPixels;
        private final long mTime;

        private Status(final int eventCount, final int stabilizationCount, final int fps,
                       final int totalEvents, final int totalPixels, final long time) {
            mEventCount = eventCount;
            mStabilizationCount = stabilizationCount;
            mFps = fps;
            mTotalEvents = totalEvents;
            mTotalPixels = totalPixels;
            mTime = time;
        }

        /**
         * Get the event count.
         *
         * @return The event count.
         */
        public int getEventCount() {
            return mEventCount;
        }

        /**
         * Get the stabilization count.
         *
         * @return The stabilization count.
         */
        public int getStabilizationCount() {
            return mStabilizationCount;
        }

        /**
         * Get the frames per second.
         *
         * @return The fps.
         */
        public int getFps() {
            return mFps;
        }

        /**
         * Get the total events.
         *
         * @return The total events.
         */
        public int getTotalEvents() {
            return mTotalEvents;
        }

        /**
         * Get the total candidate pixels.
         *
         * @return The total candidate pixels.
         */
        public int getTotalPixels() {
            return mTotalPixels;
        }

        public long getTime() {
            return mTime;
        }

        /**
         * Builder for {@link edu.uci.crayfis.widget.StatusView.Status}.
         */
        public static final class Builder {
            private int mEventCount;
            private int mStabilizationCounter;
            private int mFps;
            private int mTotalEvents;
            private int mTotalPixels;
            private long mTime;

            /**
             * Set the event count.
             *
             * @param eventCount The event count.
             * @return This instance for chaining.
             */
            public Builder setEventCount(final int eventCount) {
                mEventCount = eventCount;
                return this;
            }

            /**
             * Set the stabilization counter.
             *
             * @param stabilizationCounter The stabilization counter.
             * @return This instance for chaining.
             */
            public Builder setStabilizationCounter(final int stabilizationCounter) {
                mStabilizationCounter = stabilizationCounter;
                return this;
            }

            /**
             * Set the frames per second.
             *
             * @param fps The frames per second.
             * @return This instance for chaining.
             */
            public Builder setFps(final int fps) {
                // protect against garbage values in initial moments

                mFps = fps;
                return this;
            }

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
             * Set how long data collection has been running in seconds.
             *
             * @param time How long data collection has been running in seconds.
             * @return This instance for chaining.
             */
            public Builder setTime(final long time) {
                mTime = time;
                return this;
            }

            /**
             * Create a new instance of {@link edu.uci.crayfis.widget.StatusView.Status}.
             * @return A new instance of {@link edu.uci.crayfis.widget.StatusView.Status}.
             */
            public Status build() {
                return new Status(mEventCount, mStabilizationCounter, mFps,
                        mTotalEvents, mTotalPixels, mTime);
            }
        }
    }
}
