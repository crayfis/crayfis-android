package edu.uci.crayfis;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.BatteryManager;
import android.content.IntentFilter;

import java.util.Calendar;

import edu.uci.crayfis.util.CFLog;

public class MyBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		CFLog.d("receiver: got action=" + intent.getAction());
		
		long plugged_in= System.currentTimeMillis();

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
		boolean noAutoStart = sharedPrefs.getBoolean("prefNoAutoStart", false);

		if (!noAutoStart)
		{
		int startWait = Integer.parseInt(sharedPrefs.getString("prefStartWait","0"));
	    int startAfter = Integer.parseInt(sharedPrefs.getString("prefStartAfter","0"));
	    int startBefore = Integer.parseInt(sharedPrefs.getString("prefStartBefore","0"));

	    Calendar c = Calendar.getInstance(); 
	    int hour = c.get(Calendar.HOUR_OF_DAY);
	    if (hour > startAfter || hour < startBefore)
	    {
	    	while (System.currentTimeMillis()-plugged_in < startWait*1000)
	    	{
                // FIXME This is better served with AlarmManager
	    	}	
	    
	    	// now launch
	    	Intent it = new Intent(context, MainActivity.class);
	    	it.setAction(Intent.ACTION_MAIN);
	    	it.addCategory(Intent.CATEGORY_LAUNCHER);
	    	it.setComponent(new ComponentName(context.getPackageName(), MainActivity.class.getName()));
	    	it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    	context.getApplicationContext().startActivity(it);
	    }
		}
	}

}
