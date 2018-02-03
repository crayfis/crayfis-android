package io.crayfis.android.trigger;

import io.crayfis.android.exposure.frame.RawCameraFrame;
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
public class TriggerChain {

    private final TriggerProcessor mFirst;

    /**
     * Constructor
     *
     * @param application Application instance
     * @param state The state of the Exposure Block for which the processor is configured
     */
    public TriggerChain(CFApplication application, CFApplication.State state) {

        switch (state) {
            case STABILIZATION:
                mFirst = L0Processor.makeProcessor(application)
                        .setNext(QualityProcessor.makeProcessor(application));
                break;
            case PRECALIBRATION:
                mFirst = L0Processor.makeProcessor(application)
                        .setNext(QualityProcessor.makeProcessor(application)
                        .setNext(PreCalibrator.makeProcessor(application)));
                break;
            case CALIBRATION:
                mFirst = L0Processor.makeProcessor(application)
                        .setNext(QualityProcessor.makeProcessor(application)
                        .setNext(L1Processor.makeProcessor(application)));
                break;
            case DATA:
                mFirst = L0Processor.makeProcessor(application)
                        .setNext(QualityProcessor.makeProcessor(application)
                        .setNext(L1Processor.makeProcessor(application)
                        .setNext(L2Processor.makeProcessor(application))));
                break;
            default:
                mFirst = L0Processor.makeProcessor(application);
        }
    }

    /**
     * Add frame to the processing pipeline
     *
     * @param frame RawCameraFrame to be processed
     */
    public final void submitFrame(RawCameraFrame frame) {
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

}