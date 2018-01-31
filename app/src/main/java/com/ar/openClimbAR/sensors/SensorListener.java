package com.ar.openClimbAR.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.ar.openClimbAR.utils.IOrientationListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    private List<IOrientationListener> handler = new ArrayList<>();

    public void addListener(IOrientationListener... pHandler) {
        for (IOrientationListener i: pHandler) {
            if (!handler.contains(i)) {
                handler.add(i);
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double azimuth = 0;
        double pitch = 0;
        double roll = 0;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                float[] orientation = new float[3];
                float[] orgRMat = new float[9];
                float[] rMat = new float[9];
                float[] orientVectors;

                SensorManager.getRotationMatrixFromVector(orgRMat, event.values);

                //use the screen axis as origin. Not the sensor axis
                SensorManager.remapCoordinateSystem(orgRMat, SensorManager.AXIS_Z,SensorManager.AXIS_X, rMat);
                orientVectors = SensorManager.getOrientation( rMat, orientation);
                roll = (float)((Math.toDegrees(orientVectors[2]) + 90 ) % 360);

                //use the screen axis as origin. Not the sensor axis
                SensorManager.remapCoordinateSystem(orgRMat, SensorManager.AXIS_X,SensorManager.AXIS_MINUS_Z, rMat);

                orientVectors = SensorManager.getOrientation( rMat, orientation);

                azimuth = (Math.toDegrees(orientVectors[0]) + 180 ) % 360; // Negative because we care about the back of the phone.
                pitch = Math.toDegrees(orientVectors[1]);
                break;
        }

        for (IOrientationListener client: handler) {
            client.updateOrientation(azimuth, pitch, roll);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
