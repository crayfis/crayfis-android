package io.crayfis.android.trigger;

import android.os.AsyncTask;

import java.util.HashMap;
import java.util.concurrent.Executor;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.util.CFLog;
import io.crayfis.android.util.CFUtil;

/**
 * Created by jswaney on 1/10/18.
 */

public abstract class TriggerProcessor {

    public final CFApplication mApplication;
    public final Config config;
    public TriggerProcessor mNextProcessor;
    private Executor mExecutor;

    public int processed = 0;
    public int pass = 0;
    public int skip = 0;

    public TriggerProcessor(CFApplication application, Config config, boolean serial) {
        mApplication = application;
        this.config = config;
        mExecutor = (serial) ? AsyncTask.SERIAL_EXECUTOR : AsyncTask.THREAD_POOL_EXECUTOR;
    }

    protected static HashMap<String, String> parseConfigString(String cfgStr) {
        String[] pieces = cfgStr.split(";", 2);

        HashMap<String, String> options = new HashMap<>();
        options.put("name", pieces[0]);
        String cfgstr = pieces.length==2 ? pieces[1] : "";

        for (String c : cfgstr.split(";")) {
            String[] kv = c.split("=");
            if (kv.length != 2) continue;
            options.put(kv[0], kv[1]);
        }

        return options;
    }

    public void submitFrame(RawCameraFrame frame) {
        processed++;
        mExecutor.execute(config.makeTask(this, frame));
    }

    public TriggerProcessor setNext(TriggerProcessor next) {
        mNextProcessor = next;
        return next;
    }

    public void processResult(RawCameraFrame frame, boolean pass) { }

    public static abstract class Config {

        private final String mTaskName;
        protected final HashMap<String, Integer> mTaskConfig = new HashMap<>();

        public Config(String taskName, HashMap<String, String> keyVal, HashMap<String, Integer> keyDefault) {
            mTaskName = taskName;

            for(String key : keyDefault.keySet()) {
                int val;
                try {
                    val = Integer.parseInt(keyVal.get(key));
                } catch (NumberFormatException e) {
                    val = keyDefault.get(key);
                }
                mTaskConfig.put(key, val);
            }
        }

        public abstract Config makeNewConfig(String configStr);

        public String getName() {
            return mTaskName;
        }

        public Integer getInt(String key) {
            return mTaskConfig.get(key);
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

        public Editor edit() {
            return new Editor();
        }

        public class Editor {

            private HashMap<String, Integer> eTaskConfig;

            Editor() {
                eTaskConfig = mTaskConfig;
            }

            public Editor putInt(String key, int val) {
                eTaskConfig.put(key, val);
                return this;
            }

            public Config create() {
                StringBuilder cfgBuilder = new StringBuilder(mTaskName + ";");
                for(String key : eTaskConfig.keySet()) {
                    cfgBuilder.append(key)
                            .append("=")
                            .append(eTaskConfig.get(key));
                }
                return Config.this.makeNewConfig(cfgBuilder.toString());
            }
        }
    }

    public static abstract class Task implements Runnable {

        protected final TriggerProcessor mProcessor;
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
