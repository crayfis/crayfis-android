package io.crayfis.android;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.Calendar;

import io.crayfis.android.broadcast.AutostartReceiver;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 10/6/2017.
 */

public class ConfigureAutostartActivity extends Activity {

    private String mPreference;
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
            setAlarm();
            setResult(RESULT_OK);
            finish();
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure_autostart);

        mTimePicker = (TimePicker)findViewById(R.id.time_picker);

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

                cancelAlarm();
                setResult(RESULT_OK);
                finish();
            }
        });


    }

    private void configure() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int timeInMinutes = sharedPreferences.getInt(mPreference, 0);
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

    private void setAlarm() {

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        int timeInMin = sharedPrefs.getInt(getString(R.string.prefStartAfter), 0);
        int hour = timeInMin / 60;
        int min = timeInMin % 60;

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, min);

        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, getAlarmIntent());
    }

    private void cancelAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getAlarmIntent());
    }

    private PendingIntent getAlarmIntent() {
        Intent autostartIntent = new Intent();
        autostartIntent.setAction(AutostartReceiver.ACTION_AUTOSTART_ALARM);
        return PendingIntent.getBroadcast(this, 0,
                autostartIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
