package edu.uci.crayfis.exposure;

import android.hardware.Camera;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.camera.AcquisitionTime;
import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.trigger.L1Config;
import edu.uci.crayfis.trigger.L2Config;
import edu.uci.crayfis.trigger.L2Task.RecoEvent;
import edu.uci.crayfis.util.CFLog;

public class ExposureBlock implements Parcelable {
	public static final String TAG = "ExposureBlock";

	public final UUID run_id;
    public final UUID precal_id;

	public final AcquisitionTime start_time;
	public AcquisitionTime end_time;

	public final Location start_loc;

    public final int batteryTemp;

	public final int res_x;
	public final int res_y;
	
	public long frames_dropped;

    public final L1Config L1_trigger_config;
    public final L2Config L2_trigger_config;

    public final int L1_threshold;
	public final int L2_threshold;

    private long L1_processed;
	public long L1_pass;
	public long L1_skip;
	
	public long L2_processed;
	public long L2_pass;
	public long L2_skip;
	
	public int total_pixels;
	
	// the exposure block number within the given run
	public final int xbn;

	public final CFApplication.State daq_state;
    public long count;

	private boolean frozen = false;
	public boolean aborted = false;

    // keep track of the (average) frame statistics as well
    public double total_background = 0.0;
    public double total_max = 0.0;

    // list of raw frames that have been assigned to this XB (but not yet processed)
    private LinkedHashSet<RawCameraFrame> assignedFrames = new LinkedHashSet<>();

    // list of frames that have been processed fully and committed to the XB.
    private ArrayList<RawCameraFrame> processedFrames = new ArrayList<>();

    // list of reconstructed events to be uploaded
    private ArrayList<RecoEvent> events = new ArrayList<RecoEvent>();

    public ExposureBlock(int xbn, UUID run_id,
                         UUID precal_id,
                         String L1_config,
                         String L2_config,
                         int L1_threshold, int L2_threshold,
                         Location start_loc,
                         int batteryTemp,
                         CFApplication.State daq_state, Camera.Size sz) {
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
        this.res_x = sz.width;
        this.res_y = sz.height;

        frames_dropped = 0;
        L1_processed = L1_pass = L1_skip = 0;
        L2_processed = L2_pass = L2_skip = 0;
        total_pixels = 0;
    }

    private ExposureBlock(@NonNull final Parcel parcel) {
        run_id = (UUID) parcel.readSerializable();
        precal_id = (UUID) parcel.readSerializable();
        start_time = parcel.readParcelable(AcquisitionTime.class.getClassLoader());
        end_time = parcel.readParcelable(AcquisitionTime.class.getClassLoader());
        start_loc = parcel.readParcelable(Location.class.getClassLoader());
        batteryTemp = parcel.readInt();
        res_x = parcel.readInt();
        res_y = parcel.readInt();
        frames_dropped = parcel.readLong();
        L1_trigger_config = L1Config.makeConfig(parcel.readString());
        L2_trigger_config = L2Config.makeConfig(parcel.readString());
        L1_threshold = parcel.readInt();
        L2_threshold = parcel.readInt();
        L1_processed = parcel.readLong();
        L1_pass = parcel.readLong();
        L1_skip = parcel.readLong();
        L2_processed = parcel.readLong();
        L2_pass = parcel.readLong();
        L2_skip = parcel.readLong();
        total_pixels = parcel.readInt();
        total_background = parcel.readDouble();
        xbn = parcel.readInt();
        daq_state = (CFApplication.State) parcel.readSerializable();
        frozen = parcel.readInt() == 1;
        aborted = parcel.readInt() == 1;
        events = parcel.createTypedArrayList(RecoEvent.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeSerializable(run_id);
        dest.writeSerializable(precal_id);
        dest.writeParcelable(start_time, flags);
        dest.writeParcelable(end_time, flags);
        dest.writeParcelable(start_loc, flags);
        dest.writeInt(batteryTemp);
        dest.writeInt(res_x);
        dest.writeInt(res_y);
        dest.writeLong(frames_dropped);
        dest.writeString(L1_trigger_config.toString());
        dest.writeString(L2_trigger_config.toString());
        dest.writeInt(L1_threshold);
        dest.writeInt(L2_threshold);
        dest.writeLong(L1_processed);
        dest.writeLong(L1_pass);
        dest.writeLong(L1_skip);
        dest.writeLong(L2_processed);
        dest.writeLong(L2_pass);
        dest.writeLong(L2_skip);
        dest.writeInt(total_pixels);
        dest.writeDouble(total_background);
        dest.writeInt(xbn);
        dest.writeSerializable(daq_state);
        dest.writeInt(frozen ? 1 : 0);
        dest.writeInt(aborted ? 1 : 0);
        synchronized (events) {
            dest.writeTypedList(events);
        }
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
        boolean removed = false;
        synchronized (assignedFrames) {
            removed = assignedFrames.remove(frame);
        }
        if (!removed) {
            CFLog.e("clearFrame() called but frame was not assigned.");
        } else {
            processedFrames.add(frame);
        }
    }
	
	public void addEvent(RecoEvent event) {
		// Don't keep event information during calibration... it's too much data.
		if (daq_state == CFApplication.State.CALIBRATION) {
			return;
		}
		event.xbn = xbn;
        synchronized (events) {
            events.add(event);
        }
		
		int npix = 0;
		if (event.pixels != null) {
			npix = event.pixels.size();
		}
		total_pixels += npix;
		CFLog.d("addevt: Added event with " + npix + " pixels (total = " + total_pixels + ")");
	}
	
	// Translate between the internal and external enums
	private static DataProtos.ExposureBlock.State translateState(CFApplication.State orig) {
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

    public static final Creator<ExposureBlock> CREATOR = new Creator<ExposureBlock>() {
        @Override
        public ExposureBlock createFromParcel(final Parcel source) {
            return new ExposureBlock(source);
        }

        @Override
        public ExposureBlock[] newArray(final int size) {
            return new ExposureBlock[size];
        }
    };
}
