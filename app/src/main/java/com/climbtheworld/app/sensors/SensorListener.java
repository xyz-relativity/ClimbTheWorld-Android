package com.climbtheworld.app.sensors;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.Surface;

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
    private static float[] mRotationMatrix = new float[16];
    private static float[] mRemappedMatrix = new float[16];
    private static float[] mValues = new float[3];
    private static float[] mTruncatedRotationVector = new float[4];
    private static boolean mTruncateVector = false;
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
                // Modern rotation vector sensors
                if (!mTruncateVector) {
                    try {
                        SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                    } catch (IllegalArgumentException e) {
                        // On some Samsung devices, an exception is thrown if this vector > 4 (see #39)
                        // Truncate the array, since we can deal with only the first four values
                        mTruncateVector = true;
                        // Do the truncation here the first time the exception occurs
                        getRotationMatrixFromTruncatedVector(mRotationMatrix, event.values);
                    }
                } else {
                    // Truncate the array to avoid the exception on some devices (see #39)
                    getRotationMatrixFromTruncatedVector(mRotationMatrix, event.values);
                }

                int rot = parent.getWindowManager().getDefaultDisplay().getRotation();
                switch (rot) {
                    case Surface.ROTATION_0:
                        // No orientation change, use default coordinate system
                        SensorManager.getOrientation(mRotationMatrix, mValues);
                        // Log.d(TAG, "Rotation-0");
                        break;
                    case Surface.ROTATION_90:
                        // Log.d(TAG, "Rotation-90");
                        SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_Y,
                                SensorManager.AXIS_MINUS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    case Surface.ROTATION_180:
                        // Log.d(TAG, "Rotation-180");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_X,
                                        SensorManager.AXIS_MINUS_Y, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    case Surface.ROTATION_270:
                        // Log.d(TAG, "Rotation-270");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_Y,
                                        SensorManager.AXIS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    default:
                        // This shouldn't happen - assume default orientation
                        SensorManager.getOrientation(mRotationMatrix, mValues);
                        // Log.d(TAG, "Rotation-Unknown");
                        break;
                }
                azimuth = Math.toDegrees(mValues[0]);  // azimuth
                pitch = Math.toDegrees(mValues[1]);
                roll = Math.toDegrees(mValues[2]);
                break;
            case Sensor.TYPE_ORIENTATION:
                // Legacy orientation sensors
                azimuth = event.values[0];
                break;
            default:
                // A sensor we're not using, so return
                return;
        }

        // Correct for true north, if preference is set
        azimuth += mGeomagneticField.getDeclination();
        // Make sure value is between 0-360
        azimuth = (float) azimuth % 360.0f;

        System.out.println(Globals.virtualCamera.screenRotation);

        for (IOrientationListener client: handler) {
            client.updateOrientation(azimuth, pitch, roll);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void getRotationMatrixFromTruncatedVector(float[] rotMatrix, float[] vector) {
        System.arraycopy(vector, 0, mTruncatedRotationVector, 0, 4);
        SensorManager.getRotationMatrixFromVector(rotMatrix, mTruncatedRotationVector);
    }

    private void convertRadToDegrees(float[] orientVectors) {
        for (int i = 0; i< orientVectors.length; ++i) {
            orientVectors[i] = (float)Math.toDegrees(orientVectors[i]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
