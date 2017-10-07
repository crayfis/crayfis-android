package io.crayfis.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;

/**
 * Created by Jeff on 10/6/2017.
 */

public class ConfigureAutostartActivity extends Activity {

    public static final String PREFERENCE = "preference";
    public static final String MESSAGE = "message";

    private String mPreference;
    private String mMessage;

    private TimePicker mTimePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure_autostart);

        mTimePicker = (TimePicker)findViewById(R.id.time_picker);

        configureExtras();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int timeInMinutes = sharedPreferences.getInt(mPreference, 0);
        mTimePicker.setCurrentHour(timeInMinutes/60);
        mTimePicker.setCurrentMinute(timeInMinutes % 60);

        TextView messageView = (TextView)findViewById(R.id.time_picker_text);
        messageView.setText(mMessage);

        Button continueButton = (Button)findViewById(R.id.time_picker_continue);
        Button cancelButton = (Button)findViewById(R.id.time_picker_exit);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hour = mTimePicker.getCurrentHour();
                int minute = mTimePicker.getCurrentMinute();
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ConfigureAutostartActivity.this);

                sharedPrefs.edit()
                        .putInt(mPreference, 60*hour + minute)
                        .apply();

                setResult(RESULT_OK);
                finish();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });


    }

    private void configureExtras() {

        Bundle extras = getIntent().getExtras();

        mPreference = extras.getString(PREFERENCE);
        if(mPreference == null) {
            throw new RuntimeException("No time to set");
        }

        final String message = extras.getString(MESSAGE);
        if (message != null) {
            mMessage = message;
        } else {
            final int messageResId = extras.getInt(MESSAGE);
            if (messageResId == 0) {
                throw new RuntimeException("No message set.");
            } else {
                mMessage = getString(messageResId);
            }
        }

    }
}
