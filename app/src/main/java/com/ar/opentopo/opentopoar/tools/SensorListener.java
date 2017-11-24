package com.ar.opentopo.opentopoar.tools;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    public float gx;
    public float gy;
    public float gz;

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            switch (event.sensor.getType()){
                case Sensor.TYPE_ACCELEROMETER:
                    gx = event.values[0];
                    gy = event.values[1];
                    gz = event.values[2];
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
