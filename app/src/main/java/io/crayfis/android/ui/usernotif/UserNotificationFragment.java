package io.crayfis.android.ui.usernotif;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import io.crayfis.android.R;

/**
 * A generic fragment that displays a warning to the user.
 *
 * Use this when the app can continue to function but the conditions are not ideal.
 */
public class UserNotificationFragment extends Fragment {

    /**
     * Get an instance of {@link UserNotificationFragment}.
     *
     * @param title The title.
     * @param message The message.
     * @return {@link UserNotificationFragment}
     */
    public static UserNotificationFragment getInstance(@NonNull final String title, @NonNull final String message,
                                              boolean cancel,
                                              @NonNull final OnContinueClickListener continueClickListener) {
        final UserNotificationFragment rtn = new UserNotificationFragment();
        rtn.mTitle = title;
        rtn.mMessage = message;
        rtn.mCancel = cancel;
        rtn.mOnContinueClickListener = continueClickListener;
        return rtn;
    }

    /**
     * Get an instance of {@link UserNotificationFragment}
     *
     * @param titleResId The title resource id.
     * @param messageResId The message resource id.
     * @return {@link UserNotificationFragment}
     */
    public static UserNotificationFragment getInstance(final int titleResId, final int messageResId,
                                              @NonNull final OnContinueClickListener continueClickListener) {
        final UserNotificationFragment rtn = new UserNotificationFragment();
        rtn.mTitleResId = titleResId;
        rtn.mMessageResId = messageResId;
        rtn.mOnContinueClickListener = continueClickListener;
        return rtn;
    }

    private int mTitleResId;
    private int mMessageResId;
    private String mTitle;
    private String mMessage;

    private boolean mCancel;
    private OnContinueClickListener mOnContinueClickListener;

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rtn = inflater.inflate(R.layout.fragment_user_notification, container, false);

        final TextView header = (TextView) rtn.findViewById(R.id.warning_header);
        if (mTitleResId > 0) {
            header.setText(mTitleResId);
        } else {
            header.setText(mTitle);
        }

        final TextView message = (TextView) rtn.findViewById(R.id.warning_text);
        if (mMessageResId > 0) {
            message.setText(mMessageResId);
        } else {
            message.setText(mMessage);
        }

        // Right now, this is just for the intro message but handy to keep incase other messages have
        // links in it.
        Linkify.addLinks(message, Linkify.ALL);

        rtn.findViewById(R.id.continue_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mOnContinueClickListener != null) {
                    mOnContinueClickListener.onContinueClicked(v);
                }
            }
        });

        Button cancelBtn = rtn.findViewById(R.id.cancel_btn);
        if(mCancel) {
            cancelBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnContinueClickListener != null) {
                        mOnContinueClickListener.onCancelClicked(v);
                    }
                }
            });
        } else {
            cancelBtn.setVisibility(View.GONE);
        }

        return rtn;
    }

    /**
     * Listener for when the user has clicked continue.
     */
    public interface OnContinueClickListener {

        /**
         * The user has continued.
         *
         * @param view The view that received the click.
         */
        void onContinueClicked(@NonNull final View view);

        /**
         * The user has cancelled
         *
         * @param view The view that received the click.
         */
        void onCancelClicked(@NonNull final View view);
    }
}
