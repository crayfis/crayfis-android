package io.crayfis.android;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.crayfis.android.util.CFLog;

public class MyDisconnectReceiver extends BroadcastReceiver {
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		CFLog.d("receiver: got action=" + intent.getAction());
	}
}
