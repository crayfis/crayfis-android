package io.crayfis.android.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import java.util.Calendar;

import io.crayfis.android.R;
import io.crayfis.android.broadcast.AlarmBootReceiver;
import io.crayfis.android.broadcast.AutostartReceiver;

/**
 * Created by Jeff on 10/7/2017.
 */

public class AutostartUtil {

    private AutostartUtil() {

    }

    public static void setAlarm(Context context) {

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        int timeInMin = sharedPrefs.getInt(context.getString(R.string.prefStartAfter), 0);
        int hour = timeInMin / 60;
        int min = timeInMin % 60;

        // calculate the time of the first alarm
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, min);

        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, getAlarmIntent(context));

        // now enable the reboot receiver to reset the alarm
        ComponentName receiver = new ComponentName(context, AlarmBootReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void cancelAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getAlarmIntent(context));

        // now disable rebot receiver
        ComponentName receiver = new ComponentName(context, AlarmBootReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static PendingIntent getAlarmIntent(Context context) {
        Intent autostartIntent = new Intent();
        autostartIntent.setAction(AutostartReceiver.ACTION_AUTOSTART_ALARM);
        return PendingIntent.getBroadcast(context, 0,
                autostartIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
