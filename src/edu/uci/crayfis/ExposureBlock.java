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
	
	public DAQActivity.state daq_state;
	
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
		// Don't keep event information during calibration... it's too much data.
		if (daq_state == DAQActivity.state.CALIBRATION) {
			return;
		}
		
		events.add(event);
		total_pixels += event.pixels.size();
		Log.d("addevt", "Added event with " + event.pixels.size() + " pixels (total = " + total_pixels + ")");
	}
	
	// Translate between the internal and external enums
	private static DataProtos.ExposureBlock.State translateState(DAQActivity.state orig) {
		switch (orig) {
		case INIT:
			return DataProtos.ExposureBlock.State.INIT;
		case CALIBRATION:
			return DataProtos.ExposureBlock.State.CALIBRATION;
		case DATA:
			return DataProtos.ExposureBlock.State.DATA;
		default:
			throw new RuntimeException("Unknown state! " + orig.toString());
		}
	}
	
	public DataProtos.ExposureBlock buildProto() {
		DataProtos.ExposureBlock.Builder buf = DataProtos.ExposureBlock.newBuilder();
		
		buf.setDaqState(translateState(daq_state));
		
		buf.setL1Pass((int) L1_pass);
		buf.setL1Processed((int) L1_processed);
		buf.setL1Skip((int) L1_skip);
		buf.setL1Thresh(L1_thresh);
		
		buf.setL2Pass((int) L2_pass);
		buf.setL2Processed((int) L2_processed);
		buf.setL2Skip((int) L2_skip);
		buf.setL2Thresh(L2_thresh);
		
		buf.setGpsLat(start_loc.getLatitude());
		buf.setGpsLon(start_loc.getLongitude());
		
		// don't output event information for calibration blocks...
		// they're really huge.
		if (daq_state != DAQActivity.state.CALIBRATION) {
			for (RecoEvent evt : events) {
				buf.addEvents(evt.buildProto());
			}
		}
				
		return buf.build();
	}
	
	public byte[] toBytes() {
		DataProtos.ExposureBlock buf = buildProto();
		return buf.toByteArray();
	}
}
