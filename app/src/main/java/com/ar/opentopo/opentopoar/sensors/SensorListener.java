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
    private final float[] inR = new float[16];
    private final float[] I = new float[16];
    private final float[] acceleration = new float[3];
    private final float[] geomag = new float[3];
    private float[] orientVals = new float[3];

    private float azimuth = 0;
    private float pitch = 0;
    private float roll = 0;


    EnvironmentHandler handler;

    public SensorListener(EnvironmentHandler pHandler) {
        this.handler = pHandler;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Gets the value of the sensor that has been changed
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, acceleration, 0, acceleration.length);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, geomag, 0, geomag.length);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                float[] orientation = new float[3];
                float[] rMat = new float[9];
                SensorManager.getRotationMatrixFromVector(rMat, event.values);

                orientVals = SensorManager.getOrientation( rMat, orientation);

                azimuth = (float)(Math.toDegrees(orientVals[0]) + 360 ) % 360;
                pitch = (float)Math.toDegrees(orientVals[1]);
                roll = (float)Math.toDegrees(orientVals[2]);

//                azimuth = (azimuth + 0.1f) % 360;
//                pitch = (pitch + 1);
//                if (pitch > 90) {
//                    pitch = -90;
//                }
        }

        handler.updateOrientation(azimuth, pitch, roll);

//        // If acceleration and geomag have values then find rotation matrix
//        if (acceleration != null && geomag != null) {
//
//            // checks that the rotation matrix is found
//            boolean success = SensorManager.getRotationMatrix(inR, I,
//                    acceleration, geomag);
//            if (success) {
//                SensorManager.getOrientation(inR, orientVals);
//                azimuth = Math.toDegrees(orientVals[0]);
//                pitch = Math.toDegrees(orientVals[1]);
//                roll = Math.toDegrees(orientVals[2]);
//
//                updateView();
//            }
//        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
