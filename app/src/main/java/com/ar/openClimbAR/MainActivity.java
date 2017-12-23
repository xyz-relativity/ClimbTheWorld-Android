package com.ar.openClimbAR;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.ar.openClimbAR.ViewTopoActivity.ViewTopoActivity;
import com.ar.openClimbAR.tools.GradeConverter;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (((SensorManager) getSystemService(SENSOR_SERVICE)).getSensorList(Sensor.TYPE_GYROSCOPE).size() == 0)
        {
            displayHardwareMissingWarning();
        }

        GradeConverter.getConverter(this); //initialize the converter.
    }

    public void onClickButtonExit(View v)
    {
        System.exit(0);
    }

    public void onClickButtonViewTopo(View v)
    {
        Intent intent = new Intent(MainActivity.this, ViewTopoActivity.class);
        startActivity(intent);
    }

    private void displayHardwareMissingWarning() {
        AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setTitle(getResources().getString(R.string.gyroscope_missing));
        ad.setMessage(getResources().getString(R.string.gyroscope_missing_message));
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
