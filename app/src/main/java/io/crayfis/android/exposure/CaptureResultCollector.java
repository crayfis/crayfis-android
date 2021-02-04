package io.crayfis.android.exposure;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Pair;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.crayfis.android.daq.AcquisitionTime;
import io.crayfis.android.util.CFLog;

class CaptureResultCollector {
    private int mDropped = 0;
    private static final String TAG = "CaptureResultCollector";

    private final Deque<Pair<TotalCaptureResult, AcquisitionTime>> mResultDeque
            = new ConcurrentLinkedDeque<>();

    void add(TotalCaptureResult result, AcquisitionTime t) {
        mResultDeque.offer(new Pair<>(result, t));
        if(mResultDeque.size() > 2) {
            mDropped++;
            mResultDeque.poll();
        }
        //CFLog.d("onCaptureCompleted() " + mResultDeque.size());
    }

    int getDroppedResults(){
        return mDropped;
    }

    // if timestamp is older than oldest result, this exception is thrown:
    static class StaleTimeStampException extends Exception {}

    // for now just report any available Capture Result:
    Pair<TotalCaptureResult, AcquisitionTime> findMatch(long timestamp)
            throws StaleTimeStampException{

        final Pair<TotalCaptureResult, AcquisitionTime> pair;
        pair = mResultDeque.poll();

        // special case for correct timestamp unavailable:
        if (timestamp == -1 || timestamp == 0){
            return pair;
        }

        if (pair == null){
            return null;
        }

        TotalCaptureResult result = pair.first;

        long result_timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        if (result_timestamp == timestamp){
            return pair;
        }
        if (result_timestamp < timestamp){
            mDropped++;
            return findMatch(timestamp);
        }
        mResultDeque.offerFirst(pair);
        throw new StaleTimeStampException();
    }

}
