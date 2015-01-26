package edu.uci.crayfis;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;

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

import edu.uci.crayfis.exposure.ExposureBlock;
import edu.uci.crayfis.server.ServerCommand;
import edu.uci.crayfis.util.CFLog;

public class OutputManager extends Thread {

	private long last_cache_upload = 0;
	public final int output_queue_limit = 100;

	private ArrayBlockingQueue<AbstractMessage> outputQueue = new ArrayBlockingQueue<AbstractMessage>(output_queue_limit);

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
//
//			try {
//				toWrite = outputQueue.poll(1, TimeUnit.SECONDS);
//			}
//			catch (InterruptedException ex) {
//				// probably somebody trying to kill the thread. go back
//				// to the top, where we'll check stopRequested
//				continue;
//			}
//
//			if (toWrite == null) {
//				// oops! nothing on the queue. oh well?
//				continue;
//			}
//
//			if (chunk == null) {
//				// make a new chunk builder
//				chunk = DataProtos.DataChunk.newBuilder();
//			}
//
//			if (toWrite instanceof DataProtos.ExposureBlock) {
//				chunk.addExposureBlocks((DataProtos.ExposureBlock) toWrite);
//			}
//			else if (toWrite instanceof DataProtos.RunConfig) {
//				chunk.addRunConfigs((DataProtos.RunConfig) toWrite);
//			}
//			else if (toWrite instanceof DataProtos.CalibrationResult) {
//				chunk.addCalibrationResults((DataProtos.CalibrationResult) toWrite);
//			}
//
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


}
