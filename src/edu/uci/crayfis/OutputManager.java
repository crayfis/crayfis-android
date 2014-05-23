package edu.uci.crayfis;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.Settings.Secure;
import android.util.Log;

public class OutputManager extends Thread {
	public static final String TAG = "OutputManager";
	
	public static final int connect_timeout = 2 * 1000; // ms
	public static final int read_timeout = 5 * 1000; //ms
	
	// true if a request has been made to stop the thread
	volatile boolean stopRequested = false;
	// true if the thread is running and can process more data
	volatile boolean running = true;
	
	public final int output_queue_limit = 100;
	
	public String server_address;
	public String server_port;
	public String upload_uri;
	
	public String device_id;
	public String build_version;
	
	
	private ArrayBlockingQueue<Writable> outputQueue = new ArrayBlockingQueue<Writable>(output_queue_limit);

	Context context;
	
	public OutputManager(Context context) {
		this.context = context;
		
		server_address = context.getString(R.string.server_address);
		server_port = context.getString(R.string.server_port);
		upload_uri = context.getString(R.string.upload_uri);
		
		device_id = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
		
		build_version = "unknown";
		try {
			build_version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException ex) {
			// don't know why we'd get here...
			Log.e(TAG, "Failed to resolve build version!");
		}
	}
	
	public boolean commitExposureBlock(ExposureBlock xb) {
		boolean success = outputQueue.offer(xb);
		return success; 
	}
	
	/**
	 * Blocks until the thread has stopped
	 */
	public void stopThread() {
		stopRequested = true;
		while (running) {
			this.interrupt();
			Thread.yield();
		}
	}
	
	public interface Writable {
		public AbstractMessage buildProto();
	}

	@Override
	public void run() {
		while (! stopRequested) {
			Writable to_write = null;
			try {
				to_write = outputQueue.poll(1, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				// probably somebody trying to kill the thread. go back
				// to the top, where we'll check stopRequested
				continue;
			}
			
			if (to_write == null) {
				// oops! nothing on the queue. oh well?
				continue;
			}
			
			// Okay, so we've got a writable (currently that means exposureblock)
			// Let's upload it to the server!
			
			// don't upload CALIBRATION blocks... they tend to be really huge
			
			directUpload(to_write);
		}
		running = false;
	}
	
	private void directUpload(Writable toWrite) {
		// okay, we got an writable object, let's dump it to the server!
		String upload_url = "http://"+server_address+":"+server_port+"/devices/submit/";
		
		AbstractMessage pbuf = toWrite.buildProto();
		ByteString raw_data = pbuf.toByteString();
		
		Log.i(TAG, "Okay! We're going to upload a writable; it has " + raw_data.size() + " bytes");
		
		int serverResponseCode = 0;
		
		try {
			URL u = new URL(upload_url);
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty("Content-length", String.format("%d", raw_data.size()));
			c.setRequestProperty("Device-id", device_id);
			c.setRequestProperty("Crayfis-version", "a " + build_version);
			c.setUseCaches(false);
			c.setAllowUserInteraction(false);
			c.setConnectTimeout(connect_timeout);
			c.setReadTimeout(read_timeout);
			
			OutputStream os = c.getOutputStream();
			
			// try writing to the output stream
			raw_data.writeTo(os);
			
			Log.i(TAG, "Connecting to upload server at: " + upload_url);
			c.connect();
			serverResponseCode = c.getResponseCode();
			Log.i(TAG, "Connected! Status = " + serverResponseCode);
			
			// and now disconnect
			c.disconnect();
		}
		catch (MalformedURLException ex) {
			Log.e(TAG, "Oh noes! The upload url is malformed.", ex);
		}
		catch (IOException ex) {
			Log.e(TAG, "Oh noes! An IOException occured.", ex);
		}
	}
}