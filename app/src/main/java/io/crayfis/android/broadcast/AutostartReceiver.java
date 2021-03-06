package io.crayfis.android.broadcast;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.os.BatteryManager;
import android.content.IntentFilter;

import io.crayfis.android.main.CFApplication;
import io.crayfis.android.main.DAQService;
import io.crayfis.android.main.MainActivity;
import io.crayfis.android.util.CFLog;

public class AutostartReceiver extends BroadcastReceiver {

	public static final String ACTION_AUTOSTART_ALARM = "io.crayfis.android.AUTOSTART";

	@Override
	public void onReceive(final Context context, Intent intent) {

        CFLog.d("receiver: got action=" + intent.getAction());

        if(!MainActivity.hasPermissions(context)) return;


        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                CFApplication application = (CFApplication) context.getApplicationContext();

                // don't autostart if not charging
                if(!isCharging(context)) return;

                // check if autostart is available

                if (application.inAutostartWindow()) {

                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                    int startWait = Integer.parseInt(sharedPrefs.getString("prefStartWait","0"));

                    final Intent it = new Intent(context, DAQService.class);
                    it.setAction(Intent.ACTION_MAIN);
                    it.addCategory(Intent.CATEGORY_LAUNCHER);
                    it.setComponent(new ComponentName(context.getPackageName(), DAQService.class.getName()));
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    Handler autostartHandler = new Handler();
                    autostartHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // make sure we're still charging
                            if(isCharging(context))
                            context.startService(it);
                        }
                    }, 60000L*startWait);

                }
            }
        }, 5000L);


	}

	private boolean isCharging(Context context) {
        // Confirm that we are charging
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        if(batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return ((status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                (status == BatteryManager.BATTERY_STATUS_FULL));
    }

}
