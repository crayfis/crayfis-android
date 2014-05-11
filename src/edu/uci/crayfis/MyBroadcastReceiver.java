package edu.uci.crayfis;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		Log.d("receiver","got action="+intent.getAction());
		Intent it = new Intent(context, MainActivity.class);
		it.setAction(Intent.ACTION_MAIN);
		it.addCategory(Intent.CATEGORY_LAUNCHER);
		it.setComponent(new ComponentName(context.getPackageName(), MainActivity.class.getName()));
		it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.getApplicationContext().startActivity(it);

	}

}
