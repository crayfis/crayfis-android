package edu.uci.crayfis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
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
	
	public boolean debug_stream;
	
	public String device_id;
	public String run_id_string;
	public String build_version;
	public int build_version_code;
	
	private boolean start_uploading = false;
	
	private ArrayBlockingQueue<AbstractMessage> outputQueue = new ArrayBlockingQueue<AbstractMessage>(output_queue_limit);

	Context context;
	
	public OutputManager(DAQActivity context) {
		this.context = context;
		
		server_address = context.getString(R.string.server_address);
		server_port = context.getString(R.string.server_port);
		upload_uri = context.getString(R.string.upload_uri);
		
		debug_stream = context.getResources().getBoolean(R.bool.debug_stream);
				
		build_version = context.build_version;
		build_version_code = context.build_version_code;
		device_id = context.device_id;
		
		run_id_string = context.run_id.toString();
	}
	
	public boolean useWifiOnly() {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		return sharedPrefs.getBoolean("prefWifiOnly", true);
	}
	
	// Some utilities for determining the network state
	public NetworkInfo getNetworkInfo() {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		return cm.getActiveNetworkInfo();
	}
	
	// Check if there is *any* connectivity
	public boolean isConnected() {
		NetworkInfo info = getNetworkInfo();
		return (info != null && info.isConnected());
	}
	
	// Check if we're connected to WiFi
	public boolean isConnectedWifi() {
		NetworkInfo info = getNetworkInfo();
		return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
	}
	
	public boolean canUpload() {
		return ( (!useWifiOnly() && isConnected()) || isConnectedWifi());
	}
	
	public boolean commitExposureBlock(ExposureBlock xb) {
		if (stopRequested) {
			// oops! too late. We're shutting down.
			Log.w(TAG, "Rejecting ExposureBlock; stop has already been requested.");
			return false;
		}
		boolean success = outputQueue.offer(xb.buildProto());
		start_uploading = true;
		return success; 
	}
	
	public boolean commitRunConfig(DataProtos.RunConfig rc) {
		if (stopRequested) {
			// oops! too late. We're shutting down.
			Log.w(TAG, "Rejecting RunConfig; stop has already been requested.");
			return false;
		}
		boolean success = outputQueue.offer(rc);
		return success;
	}
	
	public boolean commitCalibrationResult(DataProtos.CalibrationResult cal) {
		if (stopRequested) {
			// oops! too late. We're shutting down.
			Log.w(TAG, "Rejecting CalibrationResult; stop has already been requested.");
			return false;
		}
		boolean success = outputQueue.offer(cal);
		start_uploading = true;
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
		
		// Loop until a stop is requested. Even after that, don't stop
		// until we've emptied the queue.
		while (! (stopRequested && outputQueue.isEmpty())) {
			// first, check to see if there's any local file(s) to be uploaded:
			uploadFile();
			
			AbstractMessage toWrite = null;
			
			try {
				toWrite = outputQueue.poll(1, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				// probably somebody trying to kill the thread. go back
				// to the top, where we'll check stopRequested
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
			else if (toWrite instanceof DataProtos.CalibrationResult) {
				chunk.addCalibrationResults((DataProtos.CalibrationResult) toWrite);
			}
			
			chunkSize = toWrite.getSerializedSize();
			
			// if we haven't gotten anything interesting to upload yet
			// (i.e. an exposure block or calibration histo), then don't upload
			// yet. This is to prevent being flooded with a bunch of RunConfigs
			// from apps starting up but not doing anything interesting.
			if (! start_uploading) {
				continue;
			}
			
			if (chunkSize > max_chunk_size || (System.currentTimeMillis() - lastUpload) > max_upload_interval) {
				// time to upload!
				outputChunk(chunk.build());
				chunk = null;
				chunkSize = 0;
				lastUpload = System.currentTimeMillis();
			}
			else {
				Log.i(TAG, "Not uploading chunk... current age = " + (System.currentTimeMillis() - lastUpload)/1e3 + ", current size = " + chunkSize);
			}
		}
		
		// before we stop running, make sure we upload any leftovers.
		if (chunk != null && start_uploading) {
			Log.i(TAG, "OutputManager is exiting... uploading last data chunk.");
			outputChunk(chunk.build());
		}
		
		running = false;
	}
	
	// output the given data chunk, either by uploading if the network
	// is available, or (TODO: by outputting to file.)
	private void outputChunk(AbstractMessage toWrite) {
		boolean uploaded = false;
		if (canUpload()) {
			// Upload to network:
			uploaded = directUpload(toWrite, run_id_string);
		}
		
		if (uploaded) {
			// Looks like everything went okay. We're done here.
			return;
		}
		
		// oops! network is either not available, or there was an
		// error during the upload.
		// TODO: write out to a file.
		Log.i(TAG, "Unable to upload to network! Falling back to local storage.");
		int timestamp = (int) (System.currentTimeMillis()/1e3);
		String filename = run_id_string + "_" + timestamp + ".bin";
		//File outfile = new File(context.getFilesDir(), filename);
		FileOutputStream outputStream;
		try {
			outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
			toWrite.writeTo(outputStream);
			outputStream.close();
			Log.i(TAG, "Data saved to " + filename);
		}
		catch (Exception ex) {
			Log.e(TAG, "Error saving to file! Dropping data.", ex);
		}
	}
	
	// check to see if there is a file, and if so, upload it.
	private void uploadFile() {
		if (!canUpload()) {
			// No network connection available... nothing to do here.
			return;
		}
		
		File localDir = context.getFilesDir();
		for (File f : localDir.listFiles()) {
			String filename = f.getName();
			if (! filename.endsWith(".bin"))
				continue;
			String[] pieces = filename.split("_");
			if (pieces.length < 2) {
				Log.w(TAG, "Skipping malformatted filename: " + filename);
				continue;
			}
			String run_id = pieces[0];
			Log.i(TAG, "Found local file from run: " + run_id);

			DataProtos.DataChunk chunk;
			try {
				chunk = DataProtos.DataChunk.parseFrom(new FileInputStream(f));
			}
			catch (IOException ex) {
				Log.e(TAG, "Failed to read local file!", ex);
				// TODO: should we remove the file?
				continue;
			}
			
			// okay, lets send the file off to upload:
			boolean uploaded = directUpload(chunk, run_id);
			
			if (uploaded) {
				// great! the file uploaded successfully.
				// now we can delete it from the local store.
				Log.i(TAG, "Successfully uploaded local file: " + filename);
				f.delete();
			}
			else {
				Log.w(TAG, "Failed to upload file: " + filename);
			}
			
			break; // only try to upload one file at a time.
		}
	}
	
	private boolean directUpload(AbstractMessage toWrite, String run_id) {
		// okay, we got an writable object, let's dump it to the server!
		String upload_url = "http://"+server_address+":"+server_port+upload_uri;
		
		ByteString raw_data = toWrite.toByteString();
		
		Log.i(TAG, "Okay! We're going to upload a chunk; it has " + raw_data.size() + " bytes");
		
		int serverResponseCode = 0;
		
		boolean success = false;
		try {
			URL u = new URL(upload_url);
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty("Content-type", "application/octet-stream");
			c.setRequestProperty("Content-length", String.format("%d", raw_data.size()));
			c.setRequestProperty("Device-id", device_id);
			c.setRequestProperty("Run-id", run_id);
			c.setRequestProperty("Crayfis-version", "a " + build_version);
			c.setRequestProperty("Crayfis-version-code", Integer.toString(build_version_code));
			if (debug_stream) {
				c.setRequestProperty("Debug-stream", "yes");
			}
			c.setUseCaches(false);
			c.setAllowUserInteraction(false);
			c.setDoOutput(true);
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
			
			if (serverResponseCode == 202 || serverResponseCode == 200) {
				// looks like everything went okay!
				success = true;
			}
		}
		catch (MalformedURLException ex) {
			Log.e(TAG, "Oh noes! The upload url is malformed.", ex);
		}
		catch (IOException ex) {
			Log.e(TAG, "Oh noes! An IOException occured.", ex);
		}
		
		return success;
	}
}