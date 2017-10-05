package io.crayfis.android.exception;

/**
 * Exception to be thrown when an illegal state transition happens.
 */
public class IllegalFsmStateException extends IllegalStateException {
    public IllegalFsmStateException() {
    }

    public IllegalFsmStateException(final String detailMessage) {
        super(detailMessage);
    }

    public IllegalFsmStateException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public IllegalFsmStateException(final Throwable cause) {
        super(cause);
    }
}
