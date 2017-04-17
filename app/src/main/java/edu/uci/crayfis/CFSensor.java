package edu.uci.crayfis;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import edu.uci.crayfis.camera.RawCameraFrame;

/**
 * Created by Jeff on 4/15/2017.
 */

public class CFSensor implements SensorEventListener {

    private SensorManager mSensorManager;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] orientation = new float[3];
    private float pressure = 0;

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

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor gravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        Sensor accelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor pressureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        Sensor sens = gravSensor;
        if (sens == null) {
            sens = accelSensor;
        }

        mSensorManager.registerListener(this, sens, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void unregister() {
        mSensorManager.unregisterListener(this);
        sInstance = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            // both gravity and accel should give the same gravity vector
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GRAVITY:
                // get the gravity vector:
                gravity[0] = event.values[0];
                gravity[1] = event.values[1];
                gravity[2] = event.values[2];
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic[0] = event.values[0];
                geomagnetic[1] = event.values[1];
                geomagnetic[2] = event.values[2];
                break;
            case Sensor.TYPE_PRESSURE:
                pressure = event.values[0];
                BUILDER.setPressure(pressure);
                return;
        }

        // now update the orientation vector
        float[] R = new float[9];
        boolean succ = SensorManager.getRotationMatrix(R, null, gravity, geomagnetic);
        if (succ) {
            SensorManager.getOrientation(R, orientation);
            BUILDER.setOrientation(orientation);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    public String getStatus() {
        return "Orientation = " + String.format("%1.2f", orientation[0]*180/Math.PI) + ", "
                + String.format("%1.2f", orientation[1]*180/Math.PI) + ", "
                + String.format("%1.2f", orientation[2]*180/Math.PI) + "\n"
                + "Pressure = " + pressure + "\n";
    }

}
