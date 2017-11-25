package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.ar.opentopo.opentopoar.R;

/**
 * Created by xyz on 11/24/17.
 */

public class SensorListener implements SensorEventListener {
    private float[] inR = new float[16];
    private float[] I = new float[16];
    private float[] gravity = new float[3];
    private float[] geomag = new float[3];
    private float[] orientVals = new float[3];

    private double azimuth = 0;
    private double pitch = 0;
    private double roll = 0;

    Activity parentActivity;
    ImageButton button = null;

    public SensorListener(Activity pActivity) {
        this.parentActivity = pActivity;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // If the sensor data is unreliable return


        // Gets the value of the sensor that has been changed
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomag = event.values.clone();
                break;
        }

        // If gravity and geomag have values then find rotation matrix
        if (gravity != null && geomag != null) {

            // checks that the rotation matrix is found
            boolean success = SensorManager.getRotationMatrix(inR, I,
                    gravity, geomag);
            if (success) {
                SensorManager.getOrientation(inR, orientVals);
                azimuth = Math.toDegrees(orientVals[0]);
                pitch = Math.toDegrees(orientVals[1]);
                roll = Math.toDegrees(orientVals[2]);

                updateView();
            }
        }
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

        System.out.println("orinet " + azimuth + " " + pitch + " " + roll);
        updateButton(button, (float)azimuth*100f, (float)pitch*100, -1f);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
