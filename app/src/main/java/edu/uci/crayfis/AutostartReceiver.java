package edu.uci.crayfis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.os.BatteryManager;
import android.content.IntentFilter;

import java.util.Calendar;

import edu.uci.crayfis.util.CFLog;

public class AutostartReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, Intent intent) {
		// TODO Auto-generated method stub
		CFLog.d("receiver: got action=" + intent.getAction());

        boolean isCharging = (intent.getAction().equals(android.content.Intent.ACTION_POWER_CONNECTED));



        if (!isCharging) {
            CFLog.d("receiver: is battery charging? ");
            // Confirm that we are charging (could have been called by "BATTERY_OKAY")
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = ((status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                          (status == BatteryManager.BATTERY_STATUS_FULL));
        }

        CFLog.d("receiver: is battery charging? "+isCharging);

        // don't autostart if not charging
        if (!isCharging) return;

        // check if autostart is selected
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean autoStart = sharedPrefs.getBoolean("prefEnableAutoStart", false);

		if (autoStart) {

			int startWait = Integer.parseInt(sharedPrefs.getString("prefStartWait","0"));
			int startAfter = Integer.parseInt(sharedPrefs.getString("prefStartAfter","0"));
			int startBefore = Integer.parseInt(sharedPrefs.getString("prefStartBefore","0"));

			Calendar c = Calendar.getInstance();
			int hour = c.get(Calendar.HOUR_OF_DAY);
			// if two of these three are true, we should autostart
			int b1 = (startAfter >= startBefore) ? 1 : 0;
			int b2 = (hour >= startAfter) ? 1: 0;
			int b3 = (hour < startBefore) ? 1 : 0;
			if (b1 + b2 + b3 >= 2) {
				final Intent it = new Intent(context, DAQService.class);
				it.setAction(Intent.ACTION_MAIN);
				it.addCategory(Intent.CATEGORY_LAUNCHER);
				it.setComponent(new ComponentName(context.getPackageName(), DAQService.class.getName()));
				it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				it.putExtra("Wait-time", startWait);

				Handler autostartHandler = new Handler();
				autostartHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						context.startService(it);
					}
				}, 1000L*startWait);

			}
		}
	}

}
