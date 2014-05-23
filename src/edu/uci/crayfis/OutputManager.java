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
	public static final int read_timeout = 5 * 1000; // ms
	
	// maximum time and size to allow between upload attempts.
	public static final int max_upload_interval = 180 * 1000; // ms
	public static final int max_chunk_size = 250000; // bytes
	
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
	
	private ArrayBlockingQueue<AbstractMessage> outputQueue = new ArrayBlockingQueue<AbstractMessage>(output_queue_limit);

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
		boolean success = outputQueue.offer(xb.buildProto());
		return success; 
	}
	
	public boolean commitRunConfig(DataProtos.RunConfig rc) {
		boolean success = outputQueue.offer(rc);
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
	
	@Override
	public void run() {
		DataProtos.DataChunk.Builder chunk = null;
		int chunkSize = 0;
		long lastUpload = 0;
		
		while (! stopRequested) {
			AbstractMessage toWrite = null;
			boolean interrupted = false;
			
			try {
				toWrite = outputQueue.poll(1, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				// probably somebody trying to kill the thread. go back
				// to the top, where we'll check stopRequested
				interrupted = true;
				continue;
			}
			
			if (toWrite == null) {
				// oops! nothing on the queue. oh well?
				continue;
			}
			
			if (chunk == null) {
				// make a new chunk builder
				chunk = DataProtos.DataChunk.newBuilder();
			}
			
			if (toWrite instanceof DataProtos.ExposureBlock) {
				chunk.addExposureBlocks((DataProtos.ExposureBlock) toWrite);
			}
			else if (toWrite instanceof DataProtos.RunConfig) {
				chunk.addRunConfigs((DataProtos.RunConfig) toWrite);
			}
			
			// I don't know if this is efficient...
			chunkSize += toWrite.toByteArray().length;
			
			if (chunkSize > max_chunk_size || (System.currentTimeMillis() - lastUpload) > max_upload_interval) {
				// time to upload!
				directUpload(chunk.build());
				chunk = null;
				chunkSize = 0;
				lastUpload = System.currentTimeMillis();
			}
			else {
				Log.i(TAG, "Not uploading chunk... current age = " + (System.currentTimeMillis() - lastUpload)/1e3 + ", current size = " + chunkSize);
			}
		}
		
		// before we stop running, make sure we upload any
		// leftovers.
		// FIXME: we should also empty the queue??
		if (chunk != null) {
			Log.i(TAG, "OutputManager is exiting... uploading last data chunk.");
			directUpload(chunk.build());
		}
		
		running = false;
	}
	
	private void directUpload(AbstractMessage toWrite) {
		// okay, we got an writable object, let's dump it to the server!
		String upload_url = "http://"+server_address+":"+server_port+upload_uri;
		
		ByteString raw_data = toWrite.toByteString();
		
		Log.i(TAG, "Okay! We're going to upload a chunk; it has " + raw_data.size() + " bytes");
		
		int serverResponseCode = 0;
		
		try {
			URL u = new URL(upload_url);
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty("Content-type", "application/octet-stream");
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