package com.climbtheworld.app.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    private List<IOrientationListener> handler = new ArrayList<>();
    private Activity parent;
    private SensorManager sensorManager;

    public SensorListener(Activity pActivity) {
        this.parent = pActivity;
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
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_GAME);
    }

    public void onPause() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double azimuth = 0;
        double pitch = 0;
        double roll = 0;

        float[] orientation = new float[3];
        float[] originRotationMatrix = new float[9];
        float[] remapRotationMatrix = new float[9];

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(originRotationMatrix, event.values);

                Display display = ((WindowManager) parent.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int rotation = display.getRotation();

                switch (rotation) {
                    case Surface.ROTATION_90:
                        SensorManager.remapCoordinateSystem(originRotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapRotationMatrix);
                        break;
                    case Surface.ROTATION_270:
                        SensorManager.remapCoordinateSystem(originRotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remapRotationMatrix);
                        break;
                    case Surface.ROTATION_180:
                        SensorManager.remapCoordinateSystem(originRotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remapRotationMatrix);
                        break;
                    case Surface.ROTATION_0:
                    default:
                        remapRotationMatrix = originRotationMatrix;
                }

                SensorManager.getOrientation( remapRotationMatrix, orientation);

                //--upside down when abs roll > 90--
//                if (Math.abs(orientation[2]) > (Math.PI/2)) {
//                    //--fix, azimuth always to true north, even when device upside down, realistic --
//                    orientation[0] = -orientation[0];
//
//                    //--fix, roll never upside down, even when device upside down, unrealistic --
//                    //orientation[2] = (float)(orientation[2] > 0 ? Math.PI - orientation[2] : - (Math.PI - Math.abs(orientation[2])));
//
//                    //--fix, pitch comes from opposite , when device goes upside down, realistic --
//                    orientation[1] = -orientation[1];
//                }

                convertRadToDegrees(orientation);

                System.out.println(rotation + " | " + Arrays.toString(orientation));

//                azimuth = (orientation[0] + 360) % 360;
                azimuth = ( Math.toDegrees( SensorManager.getOrientation( originRotationMatrix, orientation )[0] ) + 360 ) % 360;
                pitch = orientation[1];
                roll = orientation[2];
                break;
        }

        for (IOrientationListener client: handler) {
            client.updateOrientation(azimuth, pitch, roll);
        }
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
