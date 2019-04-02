package com.climbtheworld.app.sensors;

import android.app.Activity;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    private List<IOrientationListener> handler = new ArrayList<>();
    private Activity parent;
    private SensorManager sensorManager;
    private int samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL;

    // Holds sensor data
    private static float[] originRotationMatrix = new float[16];
    private static float[] remappedRotationMatrix = new float[16];
    private static float[] orientationVector = new float[3];
    private GeomagneticField mGeomagneticField;

    public SensorListener(Activity pActivity, int samplingPeriodUs) {
        this.parent = pActivity;
        this.samplingPeriodUs = samplingPeriodUs;
        addListener(Globals.virtualCamera);

        sensorManager = (SensorManager) parent.getSystemService(parent.SENSOR_SERVICE);
    }

    public void addListener(IOrientationListener... pHandler) {
        for (IOrientationListener i: pHandler) {
            if (!handler.contains(i)) {
                handler.add(i);
            }
        }
    }

    public void removeListener(IOrientationListener... pHandler) {
        for (IOrientationListener i: pHandler) {
            if (!handler.contains(i)) {
                handler.remove(i);
            }
        }
    }

    public void onResume() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), samplingPeriodUs);
        mGeomagneticField = new GeomagneticField((float) Globals.virtualCamera.decimalLatitude,
                (float) Globals.virtualCamera.decimalLongitude, (float) Globals.virtualCamera.elevationMeters,
                System.currentTimeMillis());
    }

    public void onPause() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double azimuth = Double.NaN;
        double pitch = Double.NaN;
        double roll = Double.NaN;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(originRotationMatrix, event.values);

                //align coordinates with the camera (for AR)
                SensorManager.remapCoordinateSystem(originRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedRotationMatrix);

                orientationVector = SensorManager.getOrientation(remappedRotationMatrix, orientationVector);
                azimuth = (Math.toDegrees(orientationVector[0]) + 360 ) % 360;
//                azimuth = (Math.toDegrees(orientationVector[0]) + mGeomagneticField.getDeclination() + 360 ) % 360;

//                orientationVector = SensorManager.getOrientation(remappedRotationMatrix, orientationVector);
                pitch = Math.toDegrees(orientationVector[1]) % 180;

//                orientationVector = SensorManager.getOrientation(remappedRotationMatrix, orientationVector);
                roll = (float)((Math.toDegrees(orientationVector[2])) % 180);

                for (IOrientationListener client: handler) {
                    client.updateOrientation(azimuth, pitch, roll);
                }
                break;
            default:
                // A sensor we're not using, so return
                return;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
