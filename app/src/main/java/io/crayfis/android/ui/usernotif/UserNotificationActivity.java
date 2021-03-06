package io.crayfis.android.ui.usernotif;

import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import io.crayfis.android.R;

import static io.crayfis.android.main.MainActivity.RESULT_DENY;

/**
 * Displays a user notification.
 *
 * This can be thought of as a dialog but instead takes up the full screen and does not provide a toolbar or navigation
 * drawer.  When the user clicks continue, this finishes with {@link android.app.Activity#RESULT_OK}.
 *
 * When this is displayed, data collection will stop.  Since the user is likely to pick up the phone when the notification
 * displays, data collection can not happen anyway.
 */
public class UserNotificationActivity extends AppCompatActivity implements UserNotificationFragment.OnContinueClickListener {

    public static final String TITLE = "title";
    public static final String MESSAGE = "message";
    public static final String CANCEL_BUTTON = "cancel_btn";

    private String mTitle;
    private String mMessage;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_warning);

        final Bundle extras = getIntent().getExtras();
        if (extras == null) {
            throw new RuntimeException("No extras set.");
        }

        setTitleAndMessage(extras);
        boolean cancel = extras.getBoolean(CANCEL_BUTTON, false);
        final UserNotificationFragment fragment = UserNotificationFragment.getInstance(mTitle, mMessage, cancel, this);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void setTitleAndMessage(Bundle extras) throws RuntimeException {
        final Resources resources = getResources();

        final String title = extras.getString(TITLE);
        if (title != null) {
            mTitle = title;
        } else {
            final int titleResId = extras.getInt(TITLE);
            if (titleResId == 0) {
                throw new RuntimeException("No title set.");
            } else {
                mTitle = resources.getString(titleResId);
            }
        }

        final String message = extras.getString(MESSAGE);
        if (message != null) {
            mMessage = message;
        } else {
            final int messageResId = extras.getInt(MESSAGE);
            if (messageResId == 0) {
                throw new RuntimeException("No message set.");
            } else {
                mMessage = resources.getString(messageResId);
            }
        }
    }

    @Override
    public void onContinueClicked(@NonNull final View view) {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onCancelClicked(@NonNull final View view) {
        setResult(RESULT_DENY);
        finish();
    }
}
