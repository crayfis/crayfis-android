package io.crayfis.android.trigger;

import android.os.AsyncTask;

import java.util.HashMap;
import java.util.concurrent.Executor;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.util.CFLog;

/**
 * Created by jswaney on 1/10/18.
 */

public abstract class TriggerProcessor {

    public final CFApplication mApplication;
    private final Config mConfig;
    public TriggerProcessor mNextProcessor;
    private Executor mExecutor;

    public int processed = 0;
    public int pass = 0;
    public int skip = 0;

    public TriggerProcessor(CFApplication application, String configStr, boolean serial) {
        mApplication = application;
        mConfig = parseConfig(configStr);
        mExecutor = (serial) ? AsyncTask.SERIAL_EXECUTOR : AsyncTask.THREAD_POOL_EXECUTOR;
    }

    public abstract Config makeConfig(String name, HashMap<String, String> options);

    private Config parseConfig(String configStr) {
        String[] pieces = configStr.split(";", 2);

        String name = pieces[0];
        String cfgstr = pieces.length==2 ? pieces[1] : "";
        HashMap<String, String> options = new HashMap<>();

        for (String c : cfgstr.split(";")) {
            String[] kv = c.split("=");
            if (kv.length != 2) continue;
            options.put(kv[0], kv[1]);
        }

        return makeConfig(name, options);
    }

    public void submitFrame(RawCameraFrame frame) {
        processed++;
        mExecutor.execute(mConfig.makeTask(this, frame));
    }

    public int getInt(String key) {
        String val = mConfig.mTaskConfig.get(key);
        if(val == null) {
            CFLog.w("No value found for key: " + key);
            return -1;
        }

        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            CFLog.w("Value for not an integer for key: " + key);
        }
        return -1;
    }

    public final String getConfig() {
        return mConfig.toString();
    }

    public TriggerProcessor setNext(TriggerProcessor next) {
        mNextProcessor = next;
        return next;
    }

    public void processResult(RawCameraFrame frame, boolean pass) { }

    public static abstract class Config {

        private final String mTaskName;
        protected final HashMap<String, String> mTaskConfig;

        public Config(String taskName, HashMap<String, String> taskConfig) {
            mTaskName = taskName;
            mTaskConfig = taskConfig;
        }

        @Override
        public final String toString() {
            StringBuilder cfgBuilder = new StringBuilder(mTaskName + ";");
            for(String key : mTaskConfig.keySet()) {
                cfgBuilder.append(key)
                        .append("=")
                        .append(mTaskConfig.get(key));
            }
            return cfgBuilder.toString();
        }

        public abstract Task makeTask(TriggerProcessor processor, RawCameraFrame frame);
    }

    public static abstract class Task implements Runnable {

        public final TriggerProcessor mProcessor;
        private final RawCameraFrame mFrame;

        public Task(TriggerProcessor processor, RawCameraFrame frame) {
            mProcessor = processor;
            mFrame = frame;
        }

        public abstract boolean processFrame(RawCameraFrame frame);

        @Override
        public final void run() {

            boolean pass = processFrame(mFrame);
            if(pass && mProcessor.mNextProcessor != null) {
                mProcessor.mNextProcessor.submitFrame(mFrame);
            } else {
                mFrame.clear();
            }

            mProcessor.processResult(mFrame, pass);
        }
    }
}
