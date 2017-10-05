package io.crayfis.android.widget;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Display a generic message to the user.
 */
public final class MessageView extends TextView {
    public MessageView(final Context context) {
        super(context);
    }

    public MessageView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public MessageView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Set the message for this view.  If either {@code level} or {@code message}
     * is null than the view will be blank.
     *
     * @param level The {@link io.crayfis.android.widget.MessageView.Level} of the message.
     * @param message The message.
     */
    public void setMessage(@Nullable final Level level, @Nullable final String message) {
        if (level == null || message == null) {
            setText(null);
        } else {
            switch(level) {
                case INFO:
                    setTextColor(Color.WHITE);
                    setText(message);
                    break;
                case WARNING:
                    setTextColor(Color.YELLOW);
                    setText(message);
                    break;
                case ERROR:
                    setTextColor(Color.RED);
                    setText(message);
                    break;
            }
        }
    }

    /**
     * The level of the message.
     */
    public enum Level {
        /**
         * A general imformative message for the user.
         */
        INFO,

        /**
         * A warning to the user.
         */
        WARNING,

        /**
         * Something bad has happened.
         */
        ERROR
    }



}
