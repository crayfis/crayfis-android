package edu.uci.crayfis.camera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.CFConfig;
import edu.uci.crayfis.ui.DataCollectionFragment;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 4/15/2017.
 */

public class CFSensor implements SensorEventListener {

    private SensorManager mSensorManager;
    private float[] orientation = new float[3];
    private float[] rotationMatrix = new float[9];
    private static float rotationZZ = 0;
    private float pressure = 0;

    private final CFApplication APPLICATION;
    private final RawCameraFrame.Builder BUILDER;

    private static CFSensor sInstance;

    public static CFSensor getInstance(Context context, RawCameraFrame.Builder frameBuilder) {
        if(sInstance == null) {
            sInstance = new CFSensor(context, frameBuilder);
        }
        return sInstance;
    }

    private CFSensor(Context context, final RawCameraFrame.Builder frameBuilder) {
        BUILDER = frameBuilder;
        APPLICATION = (CFApplication)context.getApplicationContext();

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        if(rotationSensor == null) {
            rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        Sensor pressureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        mSensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void unregister() {
        mSensorManager.unregisterListener(this);
        sInstance = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                rotationZZ = rotationMatrix[8];
                SensorManager.getOrientation(rotationMatrix, orientation);
                BUILDER.setOrientation(orientation)
                        .setRotationZZ(rotationZZ);
                break;
            case Sensor.TYPE_PRESSURE:
                pressure = event.values[0];
                BUILDER.setPressure(pressure);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        switch (sensor.getType()) {
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
            case Sensor.TYPE_ROTATION_VECTOR:
                if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                        && CFConfig.getInstance().getCameraSelectMode() == CFApplication.MODE_FACE_DOWN) {
                    DataCollectionFragment.getInstance().updateIdleStatus("Sensors recalibrating.  Waiting to retry");
                    APPLICATION.setApplicationState(CFApplication.State.IDLE);
                }
        }
    }

    public static boolean isFlat() {
        return Math.abs(rotationZZ) >= CFConfig.getInstance().getQualityOrientationCosine();
    }

    public String getStatus() {
        return "Orientation = " + String.format("%1.2f", orientation[0]*180/Math.PI) + ", "
                + String.format("%1.2f", orientation[1]*180/Math.PI) + ", "
                + String.format("%1.2f", orientation[2]*180/Math.PI) + " -> "
                + String.format("%1.2f", rotationZZ)+ "\n"
                + "Pressure = " + pressure + "\n";
    }

}
