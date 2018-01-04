package io.crayfis.android.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;

import io.crayfis.android.R;
import io.crayfis.android.util.AutostartUtil;

/**
 * Created by Jeff on 10/6/2017.
 */

/**
 * An activity to set start/end times for the autostart window
 */
public class ConfigureAutostartActivity extends Activity {

    private String mPreference; // sharedPreference to be changed
    private String mMessage;

    private TimePicker mTimePicker;
    private Button mContinueButton;
    private View.OnClickListener mStartOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            savePreference();
            mPreference = getString(R.string.prefStartBefore);
            mMessage = String.format(getString(R.string.about_autostart), "ENDS");
            configure();
            mContinueButton.setOnClickListener(mStopOnClickListener);
            mContinueButton.setText(getString(R.string.finish_btn));
        }
    };

    private View.OnClickListener mStopOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            savePreference();
            AutostartUtil.setAlarm(ConfigureAutostartActivity.this);
            setResult(RESULT_OK);
            finish();
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure_autostart);

        mTimePicker = (TimePicker)findViewById(R.id.time_picker);

        // start with beginning of window
        mPreference = getString(R.string.prefStartAfter);
        mMessage = String.format(getString(R.string.about_autostart), "STARTS");

        configure();

        mContinueButton = (Button)findViewById(R.id.time_picker_continue);
        Button cancelButton = (Button)findViewById(R.id.time_picker_exit);

        mContinueButton.setOnClickListener(mStartOnClickListener);
        mContinueButton.setText(getString(R.string.next_btn));

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences sharedPrefs
                        = PreferenceManager.getDefaultSharedPreferences(ConfigureAutostartActivity.this);
                sharedPrefs.edit()
                        .putBoolean(getString(R.string.prefEnableAutoStart), false)
                        .apply();

                AutostartUtil.cancelAlarm(ConfigureAutostartActivity.this);
                setResult(RESULT_OK);
                finish();
            }
        });


    }

    /**
     * Create view given mMessage & mPreference
     */
    private void configure() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int defaultTime = mPreference.equals(getString(R.string.prefStartAfter)) ? 0 : 480;
        int timeInMinutes = sharedPreferences.getInt(mPreference, defaultTime);
        mTimePicker.setCurrentHour(timeInMinutes/60);
        mTimePicker.setCurrentMinute(timeInMinutes % 60);

        TextView messageView = (TextView)findViewById(R.id.time_picker_text);
        if(sharedPreferences.getBoolean(getString(R.string.firstRun), true)) {
            mMessage += getString(R.string.first_run_autostart);
        }
        messageView.setText(mMessage);
    }

    private void savePreference() {
        int hour = mTimePicker.getCurrentHour();
        int minute = mTimePicker.getCurrentMinute();
        SharedPreferences sharedPrefs
                = PreferenceManager.getDefaultSharedPreferences(ConfigureAutostartActivity.this);

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(mPreference, 60*hour + minute);
        if(mPreference.equals(getString(R.string.prefStartBefore))) {
            editor.putBoolean(getString(R.string.prefEnableAutoStart), true);
        }
        editor.apply();
    }
}
