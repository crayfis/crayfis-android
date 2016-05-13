package edu.uci.crayfis.exposure;

import edu.uci.crayfis.SntpClient;

import android.hardware.Camera;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.UUID;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.DataProtos;
import edu.uci.crayfis.trigger.L2Task.RecoEvent;
import edu.uci.crayfis.util.CFLog;

public class ExposureBlock implements Parcelable {
	public static final String TAG = "ExposureBlock";

	public UUID run_id;

	public long start_time;
	public long end_time;

	public final long nano_offset;
    public long start_time_nano;
    public long end_time_nano;

    public long start_time_ntp;
    public long end_time_ntp;
	
	public Location start_loc;

	public int res_x = 0;
	public int res_y = 0;
	
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
	
	// the exposure block number within the given run
	public int xbn;
		
	public CFApplication.State daq_state;
	
	public boolean frozen = false;
	public boolean aborted = false;
	
	private ArrayList<RecoEvent> events = new ArrayList<RecoEvent>();
	
	public ExposureBlock() {
		nano_offset = CFApplication.getStartTimeNano();
		reset();
	}

    private ExposureBlock(@NonNull final Parcel parcel) {
        run_id = (UUID) parcel.readSerializable();
        start_time = parcel.readLong();
        end_time = parcel.readLong();
		nano_offset = parcel.readLong();
        start_time_nano = parcel.readLong();
        end_time_nano = parcel.readLong();
        start_time_ntp = parcel.readLong();
        end_time_ntp = parcel.readLong();
        start_loc = parcel.readParcelable(Location.class.getClassLoader());
        res_x = parcel.readInt();
        res_y = parcel.readInt();
        frames_dropped = parcel.readLong();
        L1_thresh = parcel.readInt();
        L2_thresh = parcel.readInt();
        L1_processed = parcel.readLong();
        L1_pass = parcel.readLong();
        L1_skip = parcel.readLong();
        L2_processed = parcel.readLong();
        L2_pass = parcel.readLong();
        L2_skip = parcel.readLong();
        total_pixels = parcel.readInt();
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
        dest.writeLong(start_time);
        dest.writeLong(end_time);
		dest.writeLong(nano_offset);
        dest.writeLong(start_time_nano);
        dest.writeLong(end_time_nano);
        dest.writeLong(start_time_ntp);
        dest.writeLong(end_time_ntp);
        dest.writeParcelable(start_loc, flags);
        dest.writeInt(res_x);
        dest.writeInt(res_y);
        dest.writeLong(frames_dropped);
        dest.writeInt(L1_thresh);
        dest.writeInt(L2_thresh);
        dest.writeLong(L1_processed);
        dest.writeLong(L1_pass);
        dest.writeLong(L1_skip);
        dest.writeLong(L2_processed);
        dest.writeLong(L2_pass);
        dest.writeLong(L2_skip);
        dest.writeInt(total_pixels);
        dest.writeInt(xbn);
        dest.writeSerializable(daq_state);
        dest.writeInt(frozen ? 1 : 0);
        dest.writeInt(aborted ? 1 : 0);
        dest.writeTypedList(events);
    }

    public void reset() {
        start_time_nano = System.nanoTime() - nano_offset;
		start_time = System.currentTimeMillis();
        start_time_ntp = SntpClient.getInstance().getNtpTime();
		frames_dropped = 0;
		L1_processed = L1_pass = L1_skip = 0;
		L2_processed = L2_pass = L2_skip = 0;
		total_pixels = 0;
	}
	
	public void freeze() {
		frozen = true;
		end_time = System.currentTimeMillis();
        end_time_nano = System.nanoTime() - nano_offset;
        end_time_ntp = SntpClient.getInstance().getNtpTime();
	}
	
	public long nanoAge() {
		if (frozen) {
			return end_time_nano - start_time_nano;
		}
		else {
			return (System.nanoTime()-nano_offset) - start_time_nano;
		}
	}
	
	public void addEvent(RecoEvent event) {
		// Don't keep event information during calibration... it's too much data.
		if (daq_state == CFApplication.State.CALIBRATION) {
			return;
		}
		event.xbn = xbn;
		events.add(event);
		
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
        buf.setGpsFixtime(start_loc.getTime());
        if (start_loc.hasAccuracy()) {
            buf.setGpsAccuracy(start_loc.getAccuracy());
        } else {
            CFLog.e("WTF no accuracy??");
        }
        if (start_loc.hasAltitude()) {
            buf.setGpsAltitude(start_loc.getAltitude());
        }

		if (res_x > 0 || res_y > 0) {
			buf.setResX(res_x);
			buf.setResY(res_y);
		}

		buf.setStartTime(start_time);
		buf.setEndTime(end_time);

        buf.setStartTimeNano(start_time_nano);
        buf.setEndTimeNano(end_time_nano);

        buf.setStartTimeNtp(start_time_ntp);
        buf.setEndTimeNtp(end_time_ntp);
		
		buf.setRunId(run_id.getLeastSignificantBits());
		
		buf.setXbn(xbn);
		
		buf.setAborted(aborted);
		
		// don't output event information for calibration blocks...
		// they're really huge.
		if (daq_state != CFApplication.State.CALIBRATION) {
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
