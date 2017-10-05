package io.crayfis.android.user;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import io.crayfis.android.CFConfig;
import io.crayfis.android.R;
import io.crayfis.android.util.ViewUtil;

/**
 * Created by jodi on 2015-06-02.
 */
public class UserStatusView extends RelativeLayout {

    private ViewGroup mUserRegistered;
    private ImageView mUserStatusAvatar;
    private TextView mUserStatusText;
    private ViewGroup mUserUnregistered;

    public UserStatusView(final Context context) {
        this(context, null);
    }

    public UserStatusView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UserStatusView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.widget_user_status, this);
        mUserRegistered = (ViewGroup) findViewById(R.id.user_status_registered);
        mUserStatusAvatar = (ImageView) findViewById(R.id.user_status_avatar);
        mUserStatusText = (TextView) findViewById(R.id.user_status_text);
        mUserUnregistered = (ViewGroup) findViewById(R.id.user_status_unregistered);
        setContentFromUser();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        setContentFromUser();
    }

    private void setContentFromUser() {
        final CFConfig cfConfig = CFConfig.getInstance();
        final String accountName = cfConfig.getAccountName();

        if (accountName == null) {
            mUserUnregistered.setVisibility(View.VISIBLE);
            mUserRegistered.setVisibility(View.GONE);
            // FIXME: This needs to pop in the user log in fragment.
        } else {
            mUserRegistered.setVisibility(View.VISIBLE);
            mUserUnregistered.setVisibility(View.GONE);
            setOnClickListener(null);

            final UserStatus userStatus = UserStatus.getByScore((int) cfConfig.getAccountScore());
            final String currentPoints = ViewUtil.formatDecimal((int) cfConfig.getAccountScore());
            final UserStatus nextLevel = userStatus.getNextLevel();
            mUserStatusAvatar.setImageResource(userStatus.getIconResource());
            final String status;
            if (nextLevel != null) {
                status = String.format("%s\n%s points - %s\n(%s until %s)",
                        accountName,
                        currentPoints,
                        userStatus.getTitle(),
                        ViewUtil.formatDecimal(nextLevel.getPoints() - userStatus.getPoints()),
                        nextLevel.getTitle());
            } else {
                status = String.format("%s\n%s points - %s",
                        accountName,
                        currentPoints,
                        userStatus.getTitle());
            }
            mUserStatusText.setText(status);
        }
    }
}
