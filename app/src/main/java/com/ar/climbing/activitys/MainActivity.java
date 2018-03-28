package com.ar.climbing.activitys;

import android.Manifest;
import android.app.AlertDialog;
import android.arch.persistence.room.Room;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import com.ar.climbing.R;
import com.ar.climbing.activitys.ViewTopoActivity.ViewTopoActivity;
import com.ar.climbing.oauth.OAuthHelper;
import com.ar.climbing.storage.database.AppDatabase;
import com.ar.climbing.tools.GradeConverter;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Globals;

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

        requestPermissions();

        if (Globals.globalConfigs == null) {
            Globals.globalConfigs = new Configs(this);
        }

        if (Globals.appDB == null) {
            Globals.appDB = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "osmCacheDb").build();
        }

        if (Globals.globalConfigs.isFirstRun()) {
            new AlertDialog.Builder(this)
                    .setTitle(getResources().getString(R.string.first_run))
                    .setMessage(getResources().getString(R.string.first_run_message, getResources().getString(R.string.app_name)))
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setPositiveButton(getResources().getString(R.string.first_run_button), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            Intent intent = new Intent(MainActivity.this, NodesDataManagerActivity.class);
                            startActivity(intent);
                        }})
                    .setNegativeButton(android.R.string.no, null).show();
        }

        initializeGlobals();
    }

    private void initializeGlobals() {
        Globals.baseContext = this.getBaseContext();
        OAuthHelper.oAuthCallbackPath = getResources().getString(R.string.custom_schema) + OAuthHelper.OAUTH_PATH;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.ButtonViewTopo:
                intent = new Intent(MainActivity.this, ViewTopoActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonViewMap:
                intent = new Intent(MainActivity.this, ViewMapActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonTools:
                intent = new Intent(MainActivity.this, ToolsActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonDonate:
                intent = new Intent(MainActivity.this, SupportMeActivity.class);
                startActivity(intent);
                break;
        }
    }

    private void displayHardwareMissingWarning() {
        AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setTitle(getResources().getString(R.string.gyroscope_missing));
        ad.setMessage(getResources().getString(R.string.gyroscope_missing_message));
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
