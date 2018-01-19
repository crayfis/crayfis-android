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

public class TriggerChain {

    private final TriggerProcessor mFirst;

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

    public final void submitFrame(RawCameraFrame frame) {
        mFirst.submitFrame(frame);
    }

    public final TriggerProcessor getProcessor(Class<? extends TriggerProcessor> cls) {
        TriggerProcessor processor = mFirst;

        while(processor != null && processor.getClass() != cls) {
            processor = processor.mNextProcessor;
        }

        return processor;
    }

}
