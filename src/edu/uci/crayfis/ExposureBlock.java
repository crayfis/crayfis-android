package edu.uci.crayfis;

import java.util.ArrayList;

import android.location.Location;
import android.util.Log;

public class ExposureBlock implements OutputManager.Writable {
	public static final String TAG = "ExposureBlock";
	
	public long run_id;

	public long start_time;
	public long end_time;
	
	public Location start_loc;
	
	public long frames_dropped;
	
	public int L1_thresh;
	public int L2_thresh;
	
	public long L1_processed;
	public long L1_pass;
	public long L1_skip;
	
	public long L2_processed;
	public long L2_pass;
	public long L2_skip;
	
	private ArrayList<Event> events = new ArrayList<Event>();
	
	public ExposureBlock() {
		reset();
	}
	public ExposureBlock(long run_id) {
		this.run_id = run_id;
		reset();
	}
	
	public void reset() {
		start_time = System.currentTimeMillis();
		frames_dropped = 0;
		L1_processed = L1_pass = L1_skip = 0;
		L2_processed = L2_pass = L2_skip = 0;
	}
	
	public void freeze() {
		end_time = System.currentTimeMillis();
	}
	
	public long age() {
		return System.currentTimeMillis() - start_time;
	}
	
	public void addEvent(ParticleReco reco) {
		Log.d(TAG, "adding new event to xb");
		
		events.add(new Event(reco));
	}
	
	public byte[] serializeBytes() {
		return new byte[0];
	}
	
	public class Event {
		public long time;
		public double lat;
		public double lon;
		
		public boolean quality;
		public float background;
		public float variance;
		
		public ArrayList<RecoPixel> pixels;
		
		public Event() {
			// no-op;
		}
		
		// Construct an event from the results of a ParticleReco object
		public Event(ParticleReco reco) {
			// todo;
		}
	}
	
	public class RecoPixel {
		public int x, y;
		public int val;
		public float avg_3, avg_5;
		public int near_max;
	}
}
