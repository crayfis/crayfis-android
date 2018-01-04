package io.crayfis.android.ui.userstatus;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.crayfis.android.R;

/**
 * A class representing the users status.
 */
public enum UserStatus {

    // These must be sorted by score or else the wrong status will be returned.
    AMATEUR("Amateur", 0, R.drawable.welcome),
    INTERMEDIATE("Intermediate", 100, R.drawable.baby),
    ADVANCED("Advanced", 1000, R.drawable.astronomer),
    ASTROPHYSICIST("Astrophysicist", 10000, R.drawable.astrophysicist),
    CARL_SAGAN("Expert", 100000000, R.drawable.expert);

    private final String mTitle; // TODO This should be a string resource but needs translation
    private final int mPoints;
    private final int mIcon;

    UserStatus(@NonNull final String title, final int points, final int icon) {
        mTitle = title;
        mPoints = points;
        mIcon = icon;
    }

    /**
     * Get the users status based on their score.
     *
     * @param score The users score.
     * @return {@link UserStatus}.
     */
    public static UserStatus getByScore(final int score) {
        final UserStatus[] statuses = UserStatus.values();
        for (int i = 0; i < statuses.length - 2; i++) {
            if (score >= statuses[i].getPoints() && score < statuses[i + 1].getPoints()) {
                return statuses[i];
            }
        }
        return statuses[statuses.length - 1];
    }

    /**
     * Get the title of this status.
     *
     * @return The title of this status.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Get the points required to reach this status.
     *
     * @return The points required to read this status.
     */
    public int getPoints() {
        return mPoints;
    }

    /**
     * Get the icon drawable resource for this status.
     *
     * @return Drawable resource for this status.
     */
    public int getIconResource() {
        return mIcon;
    }

    /**
     * Get the next level from this status.
     *
     * @return The next level or {@code null} if the user has reached the maximum level.
     */
    @Nullable
    public UserStatus getNextLevel() {
        final UserStatus[] values = values();
        for (int i = 0; i < values.length - 2; i++) {
            if (this.equals(values[i]) && i < values.length) {
                return values[i + 1];
            }
        }
        return null;
    }
}
