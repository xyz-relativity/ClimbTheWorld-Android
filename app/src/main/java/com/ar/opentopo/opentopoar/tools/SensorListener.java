package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.ar.opentopo.opentopoar.R;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    private final float[] inR = new float[16];
    private final float[] I = new float[16];
    private final float[] acceleration = new float[3];
    private final float[] geomag = new float[3];
    private final float[] orientVals = new float[3];

    private double azimuth = 0;
    private double pitch = 0;
    private double roll = 0;

    Activity parentActivity;
    ImageButton button = null;

    public SensorListener(Activity pActivity) {
        this.parentActivity = pActivity;
        setIdentityM(inR, 0);
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
                azimuth = (int) ( Math.toDegrees( SensorManager.getOrientation( rMat, orientation )[0] ) + 360 ) % 360;
        }

        updateView();

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

    private ImageButton addButtons(float x, float y, float z) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageButton bt1 = (ImageButton)inflater.inflate(R.layout.topo_display_button, null);
        buttonContainer.addView(bt1);

        updateButton(bt1, x, y, z);

        return bt1;
    }

    private void updateButton(ImageButton pButton, float x, float y, float z) {
        int size = calculateSize(z);

        pButton.getLayoutParams().height = size;
        pButton.getLayoutParams().width = size;

        pButton.setX(x);
        pButton.setY(y);
        pButton.requestLayout();
    }

    private int calculateSize(float z) {
        if (z == 0) {
            z = 1;
        }
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100/((-1)*z), parentActivity.getResources().getDisplayMetrics());
    }

    private void updateView() {
        if (button == null) {
            button = addButtons(100, 100, -10.3f);
        }

        updateButton(button, (float)(83 - azimuth), 100, -1f);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public static void setIdentityM(float[] sm, int smOffset) {
        for (int i = 0; i < 16; i++) {
            sm[smOffset + i] = 0;
        }
        for (int i = 0; i < 16; i += 5) {
            sm[smOffset + i] = 1.0f;
        }
    }

}
