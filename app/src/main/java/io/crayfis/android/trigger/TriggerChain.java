package io.crayfis.android.trigger;

import androidx.annotation.NonNull;

import java.util.Iterator;

import io.crayfis.android.exposure.ExposureBlock;
import io.crayfis.android.exposure.Frame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.trigger.L0.L0Processor;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.trigger.quality.QualityProcessor;

/**
 * Created by jswaney on 1/17/18.
 */


/**
 * Class for submitting frames through a pipeline of processors.  Configures the chain of processors
 * according to the application state and the trigger configurations
 */
public class TriggerChain implements Iterable<TriggerProcessor> {

    private final TriggerProcessor mFirst;

    /**
     * Constructor
     *
     * @param application Application instance
     * @param xb The ExposureBlock sending frames to this TriggerChain
     */
    public TriggerChain(CFApplication application, ExposureBlock xb) {

        switch (xb.daq_state) {
            case SURVEY:
                mFirst = QualityProcessor.makeProcessor(application, xb);
                break;
            case PRECALIBRATION:
                mFirst = L0Processor.makeProcessor(application, xb)
                        .setNext(QualityProcessor.makeProcessor(application, xb)
                        .setNext(PreCalibrator.makeProcessor(application, xb)));
                break;
            case CALIBRATION:
                mFirst = L0Processor.makeProcessor(application, xb)
                        .setNext(QualityProcessor.makeProcessor(application, xb)
                        .setNext(L1Processor.makeProcessor(application, xb)));
                break;
            case DATA:
                mFirst = L0Processor.makeProcessor(application, xb)
                        .setNext(QualityProcessor.makeProcessor(application, xb)
                        .setNext(L1Processor.makeProcessor(application, xb)
                        .setNext(L2Processor.makeProcessor(application, xb))));
                break;
            default:
                mFirst = L0Processor.makeProcessor(application, xb);
        }
    }

    /**
     * Add frame to the processing pipeline
     *
     * @param frame Frame to be processed
     */
    public final void submitFrame(Frame frame) {
        mFirst.submitFrame(frame);
    }

    /**
     * Return a requested type of processor, if it is present in the TriggerChain
     *
     * @param cls Class extending TriggerProcessor to return
     * @return The instance of the requested class if it exists, null otherwise
     */
    public final TriggerProcessor getProcessor(Class<? extends TriggerProcessor> cls) {
        TriggerProcessor processor = mFirst;

        while(processor != null && processor.getClass() != cls) {
            processor = processor.mNextProcessor;
        }

        return processor;
    }

    /**
     * Required method to make TriggerChain Iterable.
     *
     * @return Iterator object
     */
    @NonNull
    @Override
    public Iterator<TriggerProcessor> iterator() {

        return new Iterator<TriggerProcessor>() {

            private TriggerProcessor mCurrentProcessor = mFirst;
            private boolean mUsedFirst = false;

            @Override
            public boolean hasNext() {
                return (!mUsedFirst && mFirst != null) || mCurrentProcessor.mNextProcessor != null;
            }

            @Override
            public TriggerProcessor next() {
                if(!mUsedFirst) {
                    mUsedFirst = true;
                    return mFirst;
                }
                if(hasNext()) {
                    mCurrentProcessor = mCurrentProcessor.mNextProcessor;
                    return mCurrentProcessor;
                } else {
                    return null;
                }
            }
        };
    }

}
