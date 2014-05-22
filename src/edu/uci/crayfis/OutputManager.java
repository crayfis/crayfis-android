package edu.uci.crayfis;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.uci.crayfis.DAQActivity.TimestampedBytes;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class OutputManager extends Thread {
	// true if a request has been made to stop the thread
	volatile boolean stopRequested = false;
	// true if the thread is running and can process more data
	volatile boolean running = true;
	
	public final int output_queue_limit = 100;
	
	private ArrayBlockingQueue<Writable> outputQueue = new ArrayBlockingQueue<Writable>(output_queue_limit);

	Context context;
	
	public OutputManager(Context context) {
		this.context = context;
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
		public byte[] serializeBytes();
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
			}
		}
		running = false;
		
		/**
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		boolean doUpload = sharedPrefs.getBoolean("prefUploadData", true);
		
		while (!stopRequested) {
			// check datastorage for size, upload if too bit
			// needs to be buffered if no connection
			// Log.d("uploads"," reco? "+doReco+" uploads? "+uploadData);

			// occasionally upload files
			if (uploadData) {
				// upload every 'uploadDelta' seconds
				
				int uploaded = dstorage.uploadFiles();
				numUploads += uploaded;
				if (uploaded > 0) {
					Log.d("upload_thread", "Found " + uploaded + " files to upload");
					lastUploadTime = java.lang.System.currentTimeMillis() / 1000;
				}
				
				try {
					Thread.sleep(uploadDelta * 1000);
				}
				catch (InterruptedException ex) {
					// Somebody interrupted this thread, probably a shutdown request.
				}
			}
		}

		running = false;
		**/
	}
}