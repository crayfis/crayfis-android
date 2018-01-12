package io.crayfis.android.camera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import io.crayfis.android.exposure.frame.RawCameraFrame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.server.CFConfig;
import io.crayfis.android.R;
import io.crayfis.android.ui.navdrawer.status.LayoutStatus;

/**
 * Created by Jeff on 4/15/2017.
 */

class CFSensor implements SensorEventListener {

    private SensorManager mSensorManager;
    private float[] orientation = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] magnetic;
    private float[] gravity;
    private float pressure = 0;

    private final CFApplication APPLICATION;
    private final RawCameraFrame.Builder RCF_BUILDER;

    CFSensor(Context context, final RawCameraFrame.Builder frameBuilder) {
        RCF_BUILDER = frameBuilder;
        APPLICATION = (CFApplication)context.getApplicationContext();

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        if(rotationSensor != null) {
            mSensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // find orientation the deprecated way
            Sensor magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            Sensor gravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            magnetic = new float[3];
            gravity = new float[3];
            if(gravSensor == null) {
                gravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            mSensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, gravSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor pressureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        mSensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void unregister() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_PRESSURE:
                pressure = event.values[0];
                RCF_BUILDER.setPressure(pressure);
                return;
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetic = event.values;
                break;
            case Sensor.TYPE_GRAVITY:
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values;
                SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic);
        }

        SensorManager.getOrientation(rotationMatrix, orientation);
        RCF_BUILDER.setOrientation(orientation)
                .setRotationZZ(rotationMatrix[8]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        switch (sensor.getType()) {
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
            case Sensor.TYPE_ROTATION_VECTOR:
                if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                        && CFConfig.getInstance().getQualTrigger().startsWith("facedown")) {
                    LayoutStatus.updateIdleStatus(APPLICATION.getResources().getString(R.string.idle_sensors));
                    APPLICATION.setApplicationState(CFApplication.State.IDLE);
                }
        }
    }

    boolean isFlat() {
        return Math.abs(rotationMatrix[8]) >= Math.cos(Math.PI/180*CFConfig.getInstance().getQualityOrientation());
    }

    String getStatus() {
        return "Orientation = " + String.format("%1.2f", orientation[0]*180/Math.PI) + ", "
                + String.format("%1.2f", orientation[1]*180/Math.PI) + ", "
                + String.format("%1.2f", orientation[2]*180/Math.PI) + " -> "
                + String.format("%1.2f", rotationMatrix[8])+ "\n"
                + "Pressure = " + pressure + "\n";
    }

}
