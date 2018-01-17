package io.crayfis.android.trigger;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.trigger.L0.L0Processor;
import io.crayfis.android.trigger.L1.L1Processor;
import io.crayfis.android.trigger.L2.L2Processor;
import io.crayfis.android.trigger.precalibration.PreCalibrator;
import io.crayfis.android.trigger.quality.QualityProcessor;

/**
 * Created by jswaney on 1/17/18.
 */

public class TriggerChain {

    private TriggerChain(TriggerProcessor processor) {
        mFirst = processor;
    }

    private final TriggerProcessor mFirst;

    public static TriggerChain makeChain(CFApplication application) {
        CFConfig config = CFConfig.getInstance();

        TriggerProcessor first;
        switch (application.getApplicationState()) {
            case STABILIZATION:
                first = new L0Processor(application, config.getL0Trigger());
                first.setNext(new QualityProcessor(application, config.getQualTrigger()));
                break;
            case PRECALIBRATION:
                first = new L0Processor(application, config.getL0Trigger());
                first.setNext(new QualityProcessor(application, config.getQualTrigger()))
                        .setNext(new PreCalibrator(application, config.getPrecalTrigger()));
                break;
            case CALIBRATION:
                first = new L0Processor(application, config.getL0Trigger());
                first.setNext(new QualityProcessor(application, config.getQualTrigger()))
                        .setNext(new L1Processor(application, config.getL1Trigger()));
                break;
            case DATA:
                first = new L0Processor(application, config.getL0Trigger());
                first.setNext(new QualityProcessor(application, config.getQualTrigger()))
                        .setNext(new L1Processor(application, config.getL1Trigger()))
                        .setNext(new L2Processor(application, config.getL2Trigger()));
                break;
            default:
                first = new L0Processor(application, config.getL0Trigger());
        }

        return new TriggerChain(first);
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
