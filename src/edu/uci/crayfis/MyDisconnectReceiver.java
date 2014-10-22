package edu.uci.crayfis;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class MyDisconnectReceiver extends BroadcastReceiver {
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		Log.d("receiver","got action="+intent.getAction());
	}
}
