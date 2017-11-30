package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
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
    private float[] orientVals = new float[3];

    private float azimuth = 0;
    private float pitch = 0;
    private float roll = 0;

    Activity parentActivity;
    ImageButton button = null;

    public SensorListener(Activity pActivity) {
        this.parentActivity = pActivity;
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

//                azimuth = (float)(Math.toDegrees(orientVals[0]) + 360 ) % 360;
//                pitch = (float)Math.toDegrees(orientVals[1]);
//                roll = (float)Math.toDegrees(orientVals[2]);

                azimuth = (azimuth + 1) % 360;
//                pitch = (pitch + 1);
//                if (pitch > 90) {
//                    pitch = -90;
//                }

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

        float width = Resources.getSystem().getDisplayMetrics().widthPixels;
        float height = Resources.getSystem().getDisplayMetrics().heightPixels;

        float xPos = (azimuth * width) / 360f;
        float yPos = ((pitch + 90) * height) / 180f;

        updateButton(button, xPos, yPos, -1f);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
