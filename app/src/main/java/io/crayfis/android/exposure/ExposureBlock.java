package io.crayfis.android.exposure;

import android.location.Location;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.CFApplication;
import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.camera.RawCameraFrame;
import io.crayfis.android.trigger.L1Config;
import io.crayfis.android.trigger.L2Config;
import io.crayfis.android.trigger.L2Task.RecoEvent;
import io.crayfis.android.util.CFLog;

public class ExposureBlock {

	private final UUID run_id;
    private final UUID precal_id;

	private final AcquisitionTime start_time;
	private AcquisitionTime end_time;

	private final Location start_loc;

    private final int batteryTemp;

	private final int res_x;
	private final int res_y;
	
	long frames_dropped;

    private final L1Config L1_trigger_config;
    private final L2Config L2_trigger_config;

    private final int L1_threshold;
	private final int L2_threshold;

    public long L1_processed;
	public long L1_pass;
	public long L1_skip;
	
	public long L2_processed;
	public long L2_pass;
	public long L2_skip;
	
	private int total_pixels;
	
	// the exposure block number within the given run
	private final int xbn;

	private final CFApplication.State daq_state;
    public AtomicInteger count = new AtomicInteger();

	private boolean frozen = false;
	boolean aborted = false;

    // keep track of the (average) frame statistics as well
    public double total_background = 0.0;
    public double total_max = 0.0;

    // list of raw frames that have been assigned to this XB (but not yet processed)
    private final LinkedHashSet<RawCameraFrame> assignedFrames = new LinkedHashSet<>();

    // list of reconstructed events to be uploaded
    private ArrayList<RecoEvent> events = new ArrayList<RecoEvent>();

    public ExposureBlock(int xbn, UUID run_id,
                         UUID precal_id,
                         String L1_config,
                         String L2_config,
                         int L1_threshold, int L2_threshold,
                         Location start_loc,
                         int batteryTemp,
                         CFApplication.State daq_state,
                         int resx, int resy) {
        start_time = new AcquisitionTime();

        this.xbn = xbn;
        this.run_id = run_id;
        this.precal_id = precal_id;
        this.L1_trigger_config = L1Config.makeConfig(L1_config);
        this.L2_trigger_config = L2Config.makeConfig(L2_config);
        this.L1_threshold = L1_threshold;
        this.L2_threshold = L2_threshold;
        this.start_loc = start_loc;
        this.batteryTemp = batteryTemp;
        this.daq_state = daq_state;
        this.res_x = resx;
        this.res_y = resy;

        frames_dropped = 0;
        L1_processed = L1_pass = L1_skip = 0;
        L2_processed = L2_pass = L2_skip = 0;
        total_pixels = 0;
    }
	
	public long nanoAge() {
		if (frozen) {
			return end_time.Nano - start_time.Nano;
		}
		else {
			return (System.nanoTime()-CFApplication.getStartTimeNano()) - start_time.Nano;
		}
	}

    /***
     * Assign a camera frame to this XB. Increments the internal L1_processed counter for the XB.
     * If the XB is already frozen and the camera timestamp is after the frame's end time, it will
     * be rejected. Note that frames from before the XB started are still accepted.
     * @param frame
     * @return True if successfully assigned; false if the
     */
    public boolean assignFrame(RawCameraFrame frame) {
        boolean added;
        long frame_time = frame.getAcquiredTimeNano();
        synchronized (assignedFrames) {
            if (frozen && frame_time > end_time.Nano) {
                CFLog.e("Received frame after XB was frozen! Rejecting frame.");
                return false;
            }
            added = assignedFrames.add(frame);
        }
        if (added) {
            L1_processed++;
        } else {
            // Somebody is doing something wrong! We'll ignore it but this could be bad if a frame
            // is re-assigned after it had been processed once (and hence moved to processedFrames).
            CFLog.w("assignFrame() called but it already is assigned!");
        }
        return true;
    }

    /**
     * Freeze the XB. This means that the XB will not accept any new raw
     * camera frame assignments.
     */
    public void freeze() {
        synchronized (assignedFrames) {
            frozen = true;
            end_time = new AcquisitionTime();
        }
    }

    /**
     * Check whether the XB has been frozen. This means that the XB will not accept any new raw
     * camera frame assignments.
     * @return True if frozen.
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Check whether the XB is finalized. This means that the XB was frozen AND all raw camera
     * frames that were assigned to it have been fully processed. This is intended to be used by
     * the XB Manager to determine whether an XB which it closed can be uploaded yet.
     * @return True iff finalized.
     */
    public boolean isFinalized() {
        synchronized (assignedFrames) {
            return frozen && assignedFrames.isEmpty();
        }
    }

    public void clearFrame(RawCameraFrame frame) {
        synchronized (assignedFrames) {
            if(!assignedFrames.remove(frame)) {
                CFLog.e("clearFrame() called but frame was not assigned.");
            }
        }
    }
	
	public void addEvent(RecoEvent event) {
		// Don't keep event information during calibration... it's too much data.
		if (daq_state == CFApplication.State.CALIBRATION) {
			return;
		}
        synchronized (events) {
            events.add(event);
        }
		
		int npix = event.getNPix();

		total_pixels += npix;
		CFLog.d("addevt: Added event with " + npix + " pixels (total = " + total_pixels + ")");
	}
	
	// Translate between the internal and external enums
	private static DataProtos.ExposureBlock.State translateState(CFApplication.State orig) {
		switch (orig) {
		    case INIT:
			    return DataProtos.ExposureBlock.State.INIT;
            case PRECALIBRATION:
                return DataProtos.ExposureBlock.State.PRECALIBRATION;
            case CALIBRATION:
			    return DataProtos.ExposureBlock.State.CALIBRATION;
		    case DATA:
			    return DataProtos.ExposureBlock.State.DATA;
		    default:
			    throw new RuntimeException("Unknown state! " + orig.toString());
		}
	}
	
	public DataProtos.ExposureBlock buildProto() {
		DataProtos.ExposureBlock.Builder buf = DataProtos.ExposureBlock.newBuilder()
                .setDaqState(translateState(daq_state))
                .setL1Pass((int) L1_pass)
                .setL1Processed((int) L1_processed)
                .setL1Skip((int) L1_skip)
                .setL1Thresh(L1_threshold)
                .setL1Conf(L1_trigger_config.toString())
		
		        .setL2Pass((int) L2_pass)
		        .setL2Processed((int) L2_processed)
		        .setL2Skip((int) L2_skip)
		        .setL2Thresh(L2_threshold)
                .setL2Conf(L2_trigger_config.toString())

				.setGpsLat(start_loc.getLatitude())
		        .setGpsLon(start_loc.getLongitude())
                .setGpsFixtime(start_loc.getTime())

                .setStartTime(start_time.Sys)
                .setEndTime(end_time.Sys)
                .setStartTimeNano(start_time.Nano)
                .setEndTimeNano(end_time.Nano)
                .setStartTimeNtp(start_time.NTP)
                .setEndTimeNtp(end_time.NTP)

                .setRunId(run_id.getLeastSignificantBits())

                .setBatteryTemp(batteryTemp)
                .setXbn(xbn)
                .setAborted(aborted);

        if (start_loc.hasAccuracy()) {
            buf.setGpsAccuracy(start_loc.getAccuracy());
        } else {
            CFLog.e("WTF no accuracy??");
        }
        if (start_loc.hasAltitude()) {
            buf.setGpsAltitude(start_loc.getAltitude());
        }


		if (res_x > 0 || res_y > 0) {
			buf.setResX(res_x).setResY(res_y);
		}

        if (L1_processed > 0) {
            buf.setBgAvg(total_background / L1_processed);
        }

        // should be null for PRECALIBRATION
        if(daq_state == CFApplication.State.CALIBRATION || daq_state == CFApplication.State.DATA) {
            buf.setPrecalId(precal_id.getLeastSignificantBits());
        }
		
		// don't output event information for calibration blocks...
		// they're really huge.
		if (daq_state == CFApplication.State.DATA) {
			for (RecoEvent evt : events) {
                try {
                    buf.addEvents(evt.buildProto());
                } catch (Exception e) {
                }
			}
		}
				
		return buf.build();
	}
	
	public byte[] toBytes() {
		DataProtos.ExposureBlock buf = buildProto();
		return buf.toByteArray();
	}

    public double getPixAverage() {
        long nevt = L1_processed;
        if (nevt > 0) {
            return total_background / nevt;
        } else {
            return 0;
        }
    }

    public double getPixMax() {
        long nevt = L1_processed;
        if (nevt > 0) {
            return total_max / nevt;
        } else {
            return 0;
        }
    }

    public L1Config getL1Config() {
        return L1_trigger_config;
    }

    public int getL1Thresh() {
        return L1_threshold;
    }

    public L2Config getL2Config() {
        return L2_trigger_config;
    }

    public int getL2Thresh() {
        return L2_threshold;
    }

    public long getStartTimeNano() {
        return start_time.Nano;
    }

    public long getEndTimeNano() {
        return end_time.Nano;
    }

    public CFApplication.State getDAQState() {
        return daq_state;
    }

    public int getXBN() {
        return xbn;
    }
}
