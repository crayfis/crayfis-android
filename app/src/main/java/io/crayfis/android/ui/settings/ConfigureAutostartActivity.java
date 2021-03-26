package io.crayfis.android.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TimePicker;

import androidx.annotation.StringRes;

import io.crayfis.android.R;
import io.crayfis.android.util.AutostartUtil;

/**
 * An activity to set start/end times for the autostart window
 *
 * Created by Jeff on 10/6/2017
 */
public class ConfigureAutostartActivity extends Activity {

    private TimePicker mTimePickerStart;
    private TimePicker mTimePickerStop;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure_autostart);

        mTimePickerStart = findViewById(R.id.time_picker_start);
        mTimePickerStop = findViewById(R.id.time_picker_stop);

        setTimeFromPreferences(mTimePickerStart, R.string.prefStartAfter);
        setTimeFromPreferences(mTimePickerStop, R.string.prefStartBefore);

        Button continueButton = findViewById(R.id.time_picker_continue);
        Button cancelButton = findViewById(R.id.time_picker_exit);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreference(mTimePickerStart, R.string.prefStartAfter);
                savePreference(mTimePickerStop, R.string.prefStartBefore);
                AutostartUtil.setAlarm(ConfigureAutostartActivity.this);
                setResult(RESULT_OK);
                finish();
            }
        });

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
    private void setTimeFromPreferences(TimePicker timePicker, @StringRes int pref) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int defaultTime = (pref == R.string.prefStartAfter) ? 0 : 480;
        int timeInMinutes = sharedPreferences.getInt(getString(pref), defaultTime);
        timePicker.setHour(timeInMinutes/60);
        timePicker.setMinute(timeInMinutes % 60);
    }

    private void savePreference(TimePicker timePicker, @StringRes int pref) {
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();
        SharedPreferences sharedPrefs
                = PreferenceManager.getDefaultSharedPreferences(ConfigureAutostartActivity.this);

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(getString(pref), 60*hour + minute)
                .putBoolean(getString(R.string.prefEnableAutoStart), true)
                .apply();
    }
}
