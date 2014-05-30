package edu.uci.crayfis;

import java.util.Calendar;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		Log.d("receiver","got action="+intent.getAction());
		
		long plugged_in= System.currentTimeMillis();

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
	    		// wait
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
