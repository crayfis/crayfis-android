package io.crayfis.android.trigger;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Pair;

import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

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
    TriggerProcessor mNextProcessor;
    private Executor mExecutor;
    private final Task mTask;

    private AtomicInteger processed = new AtomicInteger();
    private AtomicInteger pass = new AtomicInteger();
    private AtomicInteger skip = new AtomicInteger();

    protected TriggerProcessor(CFApplication application, Config config, boolean serial) {
        mApplication = application;
        this.config = config;
        mExecutor = (serial) ? AsyncTask.SERIAL_EXECUTOR : AsyncTask.THREAD_POOL_EXECUTOR;
        mTask = config.makeTask(this, null);
    }

    protected static HashMap<String, String> parseConfigString(String cfgStr) {
        if(cfgStr == null) return new HashMap<>();

        // remove spaces and split by semicolons
        cfgStr =  cfgStr.replaceAll("\\s", "");
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

    protected void submitFrame(RawCameraFrame frame) {
        mTask.setFrame(frame);
        mExecutor.execute(mTask);
    }

    TriggerProcessor setNext(TriggerProcessor next) {
        mNextProcessor = next;
        return this;
    }

    protected void onFrameResult(RawCameraFrame frame, boolean pass) { }

    protected void onMaxReached() { }

    public int getProcessed() {
        return processed.intValue();
    }

    public int getPasses() {
        return pass.intValue();
    }

    public int getSkips() {
        return skip.intValue();
    }





    public static abstract class Config {

        public static final String KEY_MAXFRAMES = "maxframes";

        private final String mTaskName;
        private final HashMap<String, Integer> mTaskConfigInt = new HashMap<>();
        private final HashMap<String, Float> mTaskConfigFloat = new HashMap<>();
        private final HashMap<String, Boolean> mTaskConfigBool = new HashMap<>();
        private final HashMap<String, String> mTaskConfigStr = new HashMap<>();

        public Config(String taskName, HashMap<String, String> keyVal, HashMap<String, Object> keyDefault) {
            mTaskName = taskName;

            for(String key : keyDefault.keySet()) {

                String valString = keyVal.get(key);
                Object def = keyDefault.get(key);

                if(def instanceof Integer) {
                    try {
                        mTaskConfigInt.put(key, Integer.parseInt(valString));
                    } catch (Exception e) {
                        mTaskConfigInt.put(key, (Integer) def);
                    }
                } else if(def instanceof Float) {
                    try {
                        mTaskConfigFloat.put(key, Float.parseFloat(valString));
                    } catch (Exception e) {
                        mTaskConfigFloat.put(key, (Float) def);
                    }
                } else if(def instanceof Boolean) {
                    if(valString != null && (valString.equals("true") || valString.equals("false"))) {
                        mTaskConfigBool.put(key, Boolean.parseBoolean(valString));
                    } else {
                        mTaskConfigBool.put(key, (Boolean) def);
                    }
                } else if(def instanceof String) {
                    if(valString != null) {
                        mTaskConfigStr.put(key, valString);
                    } else {
                        mTaskConfigStr.put(key, (String) def);
                    }
                }
            }
        }

        public abstract Config makeNewConfig(String configStr);

        public String getName() {
            return mTaskName;
        }

        @Nullable
        public Integer getInt(String key) {
            return mTaskConfigInt.get(key);
        }

        @Nullable
        public Float getFloat(String key) {
            return mTaskConfigFloat.get(key);
        }

        @Nullable
        public Boolean getBoolean(String key) {
            return mTaskConfigBool.get(key);
        }

        @Nullable String getString(String key) {
            return mTaskConfigStr.get(key);
        }

        @Override
        public final String toString() {
            StringBuilder cfgBuilder = new StringBuilder(mTaskName + "; ");
            for(HashMap<String, ?> hm : new HashMap[] {mTaskConfigInt, mTaskConfigFloat, mTaskConfigBool, mTaskConfigStr}) {
                for (String key : hm.keySet()) {
                    cfgBuilder.append(key)
                            .append(" = ")
                            .append(hm.get(key))
                            .append("; ");
                }
            }
            return cfgBuilder.toString();
        }

        public abstract Task makeTask(TriggerProcessor processor, RawCameraFrame frame);

        public Editor edit() {
            return new Editor();
        }

        public class Editor {

            private final HashMap<String, Integer> eTaskConfigInt;
            private final HashMap<String, Float> eTaskConfigFloat;
            private final HashMap<String, Boolean> eTaskConfigBool;
            private final HashMap<String, String> eTaskConfigStr;


            Editor() {
                eTaskConfigInt = mTaskConfigInt;
                eTaskConfigFloat = mTaskConfigFloat;
                eTaskConfigBool = mTaskConfigBool;
                eTaskConfigStr = mTaskConfigStr;

            }

            public Editor putInt(String key, int val) {
                if(eTaskConfigInt.containsKey(key)) eTaskConfigInt.put(key, val);
                return this;
            }

            public Editor putFloat(String key, float val) {
                if(eTaskConfigFloat.containsKey(key)) eTaskConfigFloat.put(key, val);
                return this;
            }

            public Editor putBoolean(String key, boolean val) {
                if(eTaskConfigBool.containsKey(key)) eTaskConfigBool.put(key, val);
                return this;
            }

            public Editor putString(String key, String val) {
                if(eTaskConfigStr.containsKey(key)) eTaskConfigStr.put(key, val);
                return this;
            }

            public Config create() {

                StringBuilder cfgBuilder = new StringBuilder(mTaskName + ";");
                for(HashMap<String, Object> hm : new HashMap[] {eTaskConfigInt, eTaskConfigFloat, eTaskConfigBool, eTaskConfigStr})
                for(String key : hm.keySet()) {
                    cfgBuilder.append(key)
                            .append("=")
                            .append(hm.get(key))
                            .append(";");
                }
                return Config.this.makeNewConfig(cfgBuilder.toString());
            }
        }
    }

    public static abstract class Task implements Runnable {

        protected final TriggerProcessor mProcessor;
        private RawCameraFrame mFrame;

        public Task(TriggerProcessor processor, RawCameraFrame frame) {
            mProcessor = processor;
            mFrame = frame;
        }

        private void setFrame(RawCameraFrame frame) {
            mFrame = frame;
        }

        protected abstract int processFrame(RawCameraFrame frame);

        protected void onFinished() { }

        @Override
        public final void run() {

            mProcessor.processed.incrementAndGet();
            try {
                int passes = processFrame(mFrame);
                mProcessor.pass.addAndGet(passes);
                if(passes > 0 && mProcessor.mNextProcessor != null) {
                    mProcessor.mNextProcessor.submitFrame(mFrame);
                } else {
                    mProcessor.onFrameResult(mFrame, passes > 0);
                    mFrame.clear();
                }

                Integer maxFrames = mProcessor.config.getInt(Config.KEY_MAXFRAMES);
                if(maxFrames != null && mProcessor.processed.intValue() == maxFrames) {
                    onFinished();
                    mProcessor.onMaxReached();
                }
            } catch (OutOfMemoryError e) {
                mProcessor.skip.incrementAndGet();
            }
        }
    }
}