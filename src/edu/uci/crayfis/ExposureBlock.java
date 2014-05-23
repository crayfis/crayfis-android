package edu.uci.crayfis;

import java.util.ArrayList;

import edu.uci.crayfis.ParticleReco.RecoEvent;
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
	
	public int total_pixels;
	
	private ArrayList<RecoEvent> events = new ArrayList<RecoEvent>();
	
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
		total_pixels = 0;
	}
	
	public void freeze() {
		end_time = System.currentTimeMillis();
	}
	
	public long age() {
		return System.currentTimeMillis() - start_time;
	}
	
	public void addEvent(RecoEvent event) {
		events.add(event);
		total_pixels += event.pixels.size();
	}
	
	public byte[] serializeBytes() {
		return new byte[0];
	}
}
