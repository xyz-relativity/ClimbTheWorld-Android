package com.ar.opentopo.opentopoar.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.ar.opentopo.opentopoar.tools.EnvironmentHandler;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    EnvironmentHandler handler;

    public SensorListener(EnvironmentHandler pHandler) {
        this.handler = pHandler;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float azimuth = 0;
        float pitch = 0;
        float roll = 0;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                float[] orientation = new float[3];
                float[] orgRMat = new float[9];
                float[] rMat = new float[9];
                float[] orientVals;

                SensorManager.getRotationMatrixFromVector(orgRMat, event.values);

                //use the screen axis as origin. Not the sensor axis
                SensorManager.remapCoordinateSystem(orgRMat, SensorManager.AXIS_X,SensorManager.AXIS_MINUS_Z, rMat);

                orientVals = SensorManager.getOrientation( rMat, orientation);

                azimuth = (float)(Math.toDegrees(orientVals[0]) + 360 ) % 360;
                pitch = (float)Math.toDegrees(orientVals[1]);
                roll = (float)Math.toDegrees(orientVals[2]);
        }

        handler.updateOrientation(azimuth, pitch, roll);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
