package edu.uci.crayfis;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.uci.crayfis.util.CFLog;

public class OutputManager extends Thread {

    private static final CFConfig CONFIG = CFConfig.getInstance();
	
	public static final int connect_timeout = 2 * 1000; // ms
	public static final int read_timeout = 5 * 1000; // ms

	private long last_cache_upload = 0;
	
	// true if a request has been made to stop the thread
	volatile boolean stopRequested = false;
	// true if the thread is running and can process more data
	volatile boolean running = true;
	
	public final int output_queue_limit = 100;
	
	public String server_address;
	public String server_port;
	public String upload_uri;
	public boolean force_https;
	
	// Some interesting stuff from the server response
	public String current_experiment = null;
	public String device_nickname = null;
	
	public boolean debug_stream;
	
	public String device_id;
	public String run_id_string;
	public String build_version;
	public int build_version_code;
	
	public String upload_url;
	
	private boolean start_uploading = false;
	public boolean permit_upload = true;
	public boolean valid_id = true;
	
	private ArrayBlockingQueue<AbstractMessage> outputQueue = new ArrayBlockingQueue<AbstractMessage>(output_queue_limit);
    private static OutputManager sInstance;
	Context context;

    public static synchronized OutputManager getInstance(@NonNull final Context context) {
        if (sInstance == null) {
            sInstance = new OutputManager(context.getApplicationContext());
        }
        return sInstance;
    }


	private OutputManager(Context context) {
		this.context = context;
		
		server_address = context.getString(R.string.server_address);
		server_port = context.getString(R.string.server_port);
		upload_uri = context.getString(R.string.upload_uri);
		force_https = (context.getString(R.string.force_https) != "false");
		
		String upload_proto;
		if (force_https) {
			upload_proto = "https://";
		} else {
			upload_proto = "http://";
		}
		upload_url = upload_proto + server_address+":"+server_port+upload_uri;
		
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
		return permit_upload && ( (!useWifiOnly() && isConnected()) || isConnectedWifi());
	}
	
	public boolean commitExposureBlock(ExposureBlock xb) {
		if (stopRequested) {
			// oops! too late. We're shutting down.
			CFLog.w("DAQActivity Rejecting ExposureBlock; stop has already been requested.");
			return false;
		}
		boolean success = outputQueue.offer(xb.buildProto());
		start_uploading = true;
		return success;
	}

	public boolean commitRunConfig(DataProtos.RunConfig rc) {
		if (stopRequested) {
			// oops! too late. We're shutting down.
			CFLog.w("DAQActivity Rejecting RunConfig; stop has already been requested.");
			return false;
		}
		boolean success = outputQueue.offer(rc);
		return success;
	}

	public boolean commitCalibrationResult(DataProtos.CalibrationResult cal) {
		if (stopRequested) {
			// oops! too late. We're shutting down.
			CFLog.w("DAQActivity Rejecting CalibrationResult; stop has already been requested.");
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
			if (canUpload() && ((System.currentTimeMillis() - last_cache_upload) > CONFIG.getCacheUploadInterval())) {
				uploadFile();
			}

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

			chunkSize += toWrite.getSerializedSize();

			// if we haven't gotten anything interesting to upload yet
			// (i.e. an exposure block or calibration histo), then don't upload
			// yet. This is to prevent being flooded with a bunch of RunConfigs
			// from apps starting up but not doing anything interesting.
			if (! start_uploading) {
				continue;
			}

            final int maxChunkSize = CONFIG.getMaxChunkSize();
            final int maxUploadInterval = CONFIG.getMaxUploadInterval();
			if (chunkSize > maxChunkSize || (System.currentTimeMillis() - lastUpload) > maxUploadInterval * 1e3) {
				// time to upload! (or commit to file if that fails)
				outputChunk(chunk.build());
				chunk = null;
				chunkSize = 0;
				lastUpload = System.currentTimeMillis();
			}
			else {
				CFLog.i("DAQActivity Not uploading chunk... current age = " + (System.currentTimeMillis() - lastUpload)/1e3 + ", current size = " + chunkSize);
			}
		}

		// before we stop running, make sure we upload any leftovers.
		if (chunk != null && start_uploading) {
			CFLog.i("DAQActivity OutputManager is exiting... uploading last data chunk.");
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
		CFLog.i("DAQActivity Unable to upload to network! Falling back to local storage.");
		int timestamp = (int) (System.currentTimeMillis()/1e3);
		String filename = run_id_string + "_" + timestamp + ".bin";
		//File outfile = new File(context.getFilesDir(), filename);
		FileOutputStream outputStream;
		try {
			outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
			toWrite.writeTo(outputStream);
			outputStream.close();
			CFLog.i("DAQActivity Data saved to " + filename);
		}
		catch (Exception ex) {
			CFLog.e("DAQActivity Error saving to file! Dropping data.", ex);
		}
	}

	// check to see if there is a file, and if so, upload it.
	// return the number of files uploaded (currently fixed to 1 max)
	private int uploadFile() {

		File localDir = context.getFilesDir();
		int n_uploaded = 0;
		for (File f : localDir.listFiles()) {
			if (!canUpload()) {
				// No network connection available... nothing to do here.
				return n_uploaded;
			}

			String filename = f.getName();
			if (! filename.endsWith(".bin"))
				continue;
			String[] pieces = filename.split("_");
			if (pieces.length < 2) {
				CFLog.w("DAQActivity Skipping malformatted filename: " + filename);
				continue;
			}
			String run_id = pieces[0];
			CFLog.i("DAQActivity Found local file from run: " + run_id);

			DataProtos.DataChunk chunk;
			try {
				chunk = DataProtos.DataChunk.parseFrom(new FileInputStream(f));
			}
			catch (IOException ex) {
				CFLog.e("DAQActivity Failed to read local file!", ex);
				// TODO: should we remove the file?
				continue;
			}

			// okay, lets send the file off to upload:
			boolean uploaded = directUpload(chunk, run_id);

			if (uploaded) {
				// great! the file uploaded successfully.
				// now we can delete it from the local store.
				CFLog.i("DAQActivity Successfully uploaded local file: " + filename);
				f.delete();
				n_uploaded += 1;
				last_cache_upload = System.currentTimeMillis();
			}
			else {
				CFLog.w("DAQActivity Failed to upload file: " + filename);
			}

			break; // only try to upload one file at a time.
		}
		return n_uploaded;
	}

	private boolean directUpload(AbstractMessage toWrite, String run_id) {
		// okay, we got a writable object, let's dump it to the server!

		ByteString raw_data = toWrite.toByteString();

		CFLog.i("DAQActivity Okay! We're going to upload a chunk; it has " + raw_data.size() + " bytes");

		int serverResponseCode = 0;

		SharedPreferences sharedprefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
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

			String app_code = sharedprefs.getString("prefUserID", "");
			if (app_code != "") {
				c.setRequestProperty("App-code", app_code);
			}

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

			CFLog.i("DAQActivity Connecting to upload server at: " + upload_url);
			c.connect();
			serverResponseCode = c.getResponseCode();
			if (serverResponseCode == 403 || serverResponseCode == 401) {
				// server rejected us! so we are not allowed to upload.
				// oh well! we can still take data at least.

				permit_upload = false;

				if (serverResponseCode == 401) {
					// server rejected us because our app code is invalid.
					CFLog.w("ww2: foo ctx" + context.getApplicationContext());
					SharedPreferences.Editor editor = sharedprefs.edit();
					editor.putBoolean("badID", true);
					editor.apply();
					CFLog.w("DAQActivity setting bad ID flag!");
					valid_id = false;
				}
				return false;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));
			String line;
			StringBuilder sb = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}

			JSONObject jObject;
			try {
				jObject = new JSONObject(sb.toString());
                // FIXME I think this involves creating ServerCommand object and upating the preferences, but this has 2 new values.
//				context.updateSettings(jObject);
//
//				if (jObject.has("experiment")) {
//					current_experiment = jObject.getString("experiment");
//				}
//				if (jObject.has("nickname")) {
//					device_nickname = jObject.getString("nickname");
//				}
			}
			catch (JSONException ex) {
				CFLog.w("DAQActivity Warning: malformed JSON response from server.");
			}

			CFLog.i("DAQActivity Connected! Status = " + serverResponseCode);

			// and now disconnect
			c.disconnect();

			if (serverResponseCode == 202 || serverResponseCode == 200) {
				// looks like everything went okay!
				success = true;
				// make sure we clear the badID flag.
				SharedPreferences.Editor editor = sharedprefs.edit();
				editor.putBoolean("badID", true);
				editor.apply();
				valid_id = true;
			}
		}
		catch (MalformedURLException ex) {
			CFLog.e("DAQActivity Oh noes! The upload url is malformed.", ex);
		}
		catch (IOException ex) {
			CFLog.e("DAQActivity Oh noes! An IOException occured.", ex);
		}
		
		return success;
	}
}