package io.crayfis.android.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.crayfis.android.AutostartUtil;
import io.crayfis.android.R;

/**
 * Created by Jeff on 10/7/2017.
 */

/**
 * A receiver that resets the autostart alarm after the device has been rebooted
 */
public class AlarmBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            if(sharedPrefs.getBoolean(context.getString(R.string.prefEnableAutoStart), false)) {
                AutostartUtil.setAlarm(context);
            }
        }
    }
}
