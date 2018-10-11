package io.crayfis.android.exposure;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.location.Location;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.crayfis.android.R;
import io.crayfis.android.ScriptC_weight;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.DataProtos;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.trigger.TriggerChain;
import io.crayfis.android.trigger.TriggerProcessor;
import io.crayfis.android.util.Histogram;
import io.crayfis.android.camera.AcquisitionTime;
import io.crayfis.android.ui.navdrawer.gallery.GalleryUtil;
import io.crayfis.android.ui.navdrawer.gallery.LayoutGallery;
import io.crayfis.android.ui.navdrawer.live_view.LayoutLiveView;
import io.crayfis.android.util.CFLog;

public class ExposureBlock {

    private final CFApplication APPLICATION;

	public final UUID run_id;
    public final int hot_hash;
    public final int wgt_hash;
    public final int camera_id;
    final Boolean camera_facing_back;

	private final AcquisitionTime start_time;
	private AcquisitionTime end_time;

	private final Location start_loc;

    private final int batteryTemp;
    private int batteryEndTemp;

	public final int res_x;
	public final int res_y;

	final ScriptC_weight weights;
	public final TriggerChain TRIGGER_CHAIN;
	
	private int total_pixels;
	
	// the exposure block number within the given run
	public final int xbn;

	public final CFApplication.State daq_state;
    public AtomicInteger count = new AtomicInteger();

	private boolean frozen = false;
	boolean aborted = false;

    // keep track of the (average) frame statistics as well
    public final Histogram underflow_hist;

    // list of raw frames that have been assigned to this XB (but not yet processed)
    private final LinkedHashSet<RawCameraFrame> assignedFrames = new LinkedHashSet<>();

    // list of reconstructed events to be uploaded
    private final ArrayList<DataProtos.Event> events = new ArrayList<>();

    ExposureBlock(CFApplication application,
                         int xbn,
                         UUID run_id,
                         int hot_hash,
                         int wgt_hash,
                         int camera_id,
                         @Nullable Boolean camera_facing_back,
                         ScriptC_weight weights,
                         TriggerChain TRIGGER_CHAIN,
                         Location start_loc,
                         int batteryTemp,
                         CFApplication.State daq_state,
                         int resx,
                         int resy) {
        start_time = new AcquisitionTime();

        this.APPLICATION = application;
        this.xbn = xbn;
        this.run_id = run_id;
        this.hot_hash = hot_hash;
        this.wgt_hash = wgt_hash;
        this.camera_id = camera_id;
        this.camera_facing_back = camera_facing_back;
        this.weights = weights;
        this.TRIGGER_CHAIN = TRIGGER_CHAIN;
        this.underflow_hist = new Histogram(CFConfig.getInstance().getL1Threshold()+1);
        this.start_loc = start_loc;
        this.batteryTemp = batteryTemp;
        this.daq_state = daq_state;
        this.res_x = resx;
        this.res_y = resy;

        total_pixels = 0;

    }

    /***
     * Assign a camera frame to this XB. Increments the internal L1_processed counter for the XB.
     * If the XB is already frozen and the camera timestamp is after the frame's end time, it will
     * be rejected. Note that frames from before the XB started are still accepted.
     * @param frame
     * @return True if successfully assigned; false if the
     */
    public void tryAssignFrame(RawCameraFrame frame) {

        count.incrementAndGet();

        long frame_time = frame.getAcquiredTimeNano();
        synchronized (assignedFrames) {
            if (frozen && frame_time > end_time.Nano) {
                CFLog.e("Received frame after XB was frozen! Rejecting frame.");
                frame.retire();
                return;
            }
            if(!assignedFrames.add(frame)) {
                // Somebody is doing something wrong! We'll ignore it but this could be bad if a frame
                // is re-assigned after it had been processed once (and hence moved to processedFrames).
                CFLog.w("assignFrame() called but it already is assigned!");
            }
        }

        // If we made it here, we can submit the XB to the L1Processor.
        // It will pop the assigned frame from the XB's internal list, and will also handle
        // recycling the buffers.
        TRIGGER_CHAIN.submitFrame(frame);
    }

    /**
     * Freeze the XB. This means that the XB will not accept any new raw
     * camera frame assignments.
     */
    void freeze() {
        synchronized (assignedFrames) {
            frozen = true;
            end_time = new AcquisitionTime();
            batteryEndTemp = APPLICATION.getBatteryTemp();
        }
    }

    /**
     * Check whether the XB has been frozen. This means that the XB will not accept any new raw
     * camera frame assignments.
     * @return True if frozen.
     */
    boolean isFrozen() {
        return frozen;
    }

    /**
     * Check whether the XB is finalized. This means that the XB was frozen AND all raw camera
     * frames that were assigned to it have been fully processed. This is intended to be used by
     * the XB Manager to determine whether an XB which it closed can be uploaded yet.
     * @return True iff finalized.
     */
    boolean isFinalized() {
        synchronized (assignedFrames) {
            return frozen && assignedFrames.isEmpty();
        }
    }

    public void clearFrame(RawCameraFrame frame) {
        synchronized (assignedFrames) {
            if (frame.uploadRequested()) {
                // this frame passed some trigger, so add it to the XB
                DataProtos.Event event = frame.getEvent();
                synchronized (events) {
                    events.add(event);
                }

                // update UI
                LayoutLiveView.addEvent(event);

                if(event.getPixelsCount() >= LayoutGallery.getGalleryCount()
                        || event.hasByteBlock() && event.getByteBlock().getXCount() >= LayoutGallery.getGalleryCount()) {
                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(APPLICATION);
                    if(sharedPrefs.getBoolean(APPLICATION.getString(R.string.prefEnableGallery), false)
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                            || APPLICATION.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED)) {

                        GalleryUtil.saveImage(event);
                    }
                }

                // add to XB pixels
                int npix = event.getPixelsCount();
                if(event.hasByteBlock()) {
                    npix = event.getByteBlock().getXCount();
                }

                total_pixels += npix;
                CFLog.d("addevt: Added event with " + npix + " pixels (total = " + total_pixels + ")");
            }
            if(!assignedFrames.remove(frame)) {
                CFLog.e("clearFrame() called but frame was not assigned.");
                CFLog.d("assigned frames: " + assignedFrames.size());
            }
        }
    }
	
	// Translate between the internal and external enums
	private static DataProtos.ExposureBlock.State translateState(CFApplication.State orig) {
		switch (orig) {
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
	
	DataProtos.ExposureBlock buildProto() {
		DataProtos.ExposureBlock.Builder buf = DataProtos.ExposureBlock.newBuilder()
                .setDaqState(translateState(daq_state));

		for(TriggerProcessor processor : TRIGGER_CHAIN) {
		    buf.addConfig(processor.getClass().getSimpleName() + ": " + processor.config.toString())
                    .addProcessed(processor.getProcessed())
                    .addPass(processor.getPasses())
                    .addSkip(processor.getSkips());
        }

        TriggerProcessor L1 = TRIGGER_CHAIN.getProcessor(L1Processor.class);

        if(L1 != null) {
            buf.setL1Thresh(L1.config.getInt(L1Processor.KEY_L1_THRESH));
        }

        TriggerProcessor L2 = TRIGGER_CHAIN.getProcessor(L2Processor.class);

        if(L2 != null) {
            buf.setL2Thresh(L2.config.getInt(L2Processor.KEY_L2_THRESH));
        }

        buf.setGpsLat(start_loc.getLatitude())
		        .setGpsLon(start_loc.getLongitude())
                .setGpsFixtime(start_loc.getTime())

                .setStartTime(start_time.Sys)
                .setEndTime(end_time.Sys)
                .setStartTimeNano(start_time.Nano)
                .setEndTimeNano(end_time.Nano)
                .setStartTimeNtp(start_time.NTP)
                .setEndTimeNtp(end_time.NTP)

                .setRunId(run_id.getLeastSignificantBits())
                .setRunIdHi(run_id.getMostSignificantBits())

                .setBatteryTemp(batteryTemp)
                .setBatteryEndTemp(batteryEndTemp)
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

        buf.addAllHist(underflow_hist);

        // should be null for PRECALIBRATION
        if(daq_state == CFApplication.State.CALIBRATION || daq_state == CFApplication.State.DATA) {
            buf.setHotHash(hot_hash)
                    .setWgtHash(wgt_hash);
        }
		
		// don't output event information for calibration blocks...
		// they're really huge.
		if (daq_state == CFApplication.State.DATA) {
			for (DataProtos.Event evt : events) {
                try {
                    buf.addEvents(evt);
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

    long getStartTimeNano() {
        return start_time.Nano;
    }

    long getEndTimeNano() {
        return end_time.Nano;
    }
}
