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

/**
 * Class representing a single link in the TriggerChain
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

    /**
     * Constructor
     *
     * @param application The Application instance
     * @param config The TriggerProcessor.Config to be implemented
     * @param serial Determines the type of AsyncTask Executor to use: SERIAL_EXECUTOR if true,
     *               THREAD_POOL_EXECUTOR if false
     */
    protected TriggerProcessor(CFApplication application, Config config, boolean serial) {
        mApplication = application;
        this.config = config;
        mExecutor = (serial) ? AsyncTask.SERIAL_EXECUTOR : AsyncTask.THREAD_POOL_EXECUTOR;
        mTask = config.makeTask(this);
    }

    /**
     * Parses a string into a HashMap of field/value pairs to be interpreted by the TriggerProcessor
     *
     * @param cfgStr A string of the form "TaskName(; field1=value1; field2=value2; ...)
     * @return A HashMap with pairs: ("name", "TaskName"), ("field1, "value1"), ...
     */
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

    /**
     * Hands frame to the Task to be executed
     *
     * @param frame RawCameraFrame to be processed
     */
    protected void submitFrame(final RawCameraFrame frame) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                int nFrames = processed.incrementAndGet();
                try {
                    int passes = mTask.processFrame(frame);
                    pass.addAndGet(passes);
                    if(passes > 0 && mNextProcessor != null) {
                        mNextProcessor.submitFrame(frame);
                    } else {
                        onFrameResult(frame, passes > 0);
                        frame.clear();
                    }

                    Integer maxFrames = config.getInt(Config.KEY_MAXFRAMES);
                    if(maxFrames != null && nFrames == maxFrames) {
                        mTask.onMaxReached();
                        onMaxReached();
                    }
                } catch (OutOfMemoryError e) {
                    skip.incrementAndGet();
                }
            }
        });
    }

    /**
     * Adds a link after this TriggerProcessor in the TriggerChain
     *
     * @param next TriggerProcessor that follows this one in the TriggerChain
     * @return self
     */
    TriggerProcessor setNext(TriggerProcessor next) {
        mNextProcessor = next;
        return this;
    }

    /**
     * Callback after frame is processed by this TriggerProcessor
     *
     * @param frame RawCameraFrame processed
     * @param pass Whether the Task returned true or false
     */
    protected void onFrameResult(RawCameraFrame frame, boolean pass) { }

    /**
     * Callback after the TriggerProcessor has processed the number of frames given by
     * Config.getInt(KEY_MAXFRAMES)
     */
    protected void onMaxReached() { }

    /**
     * Get the number of frames supplied to the TriggerProcessor
     *
     * @return int
     */
    public int getProcessed() {
        return processed.intValue();
    }

    /**
     * Get the total number of passes assigned by the Task
     *
     * @return int
     */
    public int getPasses() {
        return pass.intValue();
    }

    /**
     * Get the number of times an OOM error occurred in this processor's Task
     *
     * @return int
     */
    public int getSkips() {
        return skip.intValue();
    }


    /**
     * Configuration class to be applied by the TriggerProcessor in creating a Task to run
     */
    public static abstract class Config {

        public static final String KEY_MAXFRAMES = "maxframes";

        private final String mTaskName;
        private final HashMap<String, Integer> mTaskConfigInt = new HashMap<>();
        private final HashMap<String, Float> mTaskConfigFloat = new HashMap<>();
        private final HashMap<String, Boolean> mTaskConfigBool = new HashMap<>();
        private final HashMap<String, String> mTaskConfigStr = new HashMap<>();

        /**
         * Constructor
         *
         * @param taskName String name of the Task to be created
         * @param keyVal (String, String) HashMap of fields and values supplied by the parsed
         *               config String
         * @param keyDefault (String, Object) HashMap of fields for the requested Task, plus their
         *                   default values if not supplied in keyVal
         */
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
            String cfgStr = cfgBuilder.toString();
            return cfgStr.substring(0, cfgStr.length()-2);
        }

        /**
         * Map the Config's taskName to an existing Task and instantiate it in a TriggerProcessor
         *
         * @param processor TriggerProcessor to manage the task
         * @return
         */
        public abstract Task makeTask(TriggerProcessor processor);

        public Editor edit() {
            return new Editor();
        }

        /**
         * A class for making changes to Config fields
         */
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

    /**
     * Runnable executed by the TriggerProcessor
     */
    public static abstract class Task {

        protected final TriggerProcessor mProcessor;

        /**
         * Constructor
         *
         * @param processor TriggerProcessor executing this Task
         */
        public Task(TriggerProcessor processor) {
            mProcessor = processor;
        }

        /**
         * Method assigning the number of "passes" to associate with this frame
         *
         * @param frame RawCameraFrame to be processed
         * @return integer number of "passes"
         */
        protected abstract int processFrame(RawCameraFrame frame);

        /**
         * Callback after the TriggerProcessor has processed the number of frames given by
         * Config.getInt(KEY_MAXFRAMES).  Executes before TriggerProcessor.onMaxReached()
         */
        protected void onMaxReached() { }
    }
}