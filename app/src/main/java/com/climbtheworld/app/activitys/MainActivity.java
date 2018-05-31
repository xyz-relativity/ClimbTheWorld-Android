package com.climbtheworld.app.activitys;

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
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((TextView) findViewById(R.id.textDonate)). setText(getString(R.string.support_me, getString(R.string.app_name)));

        if (((SensorManager) getSystemService(SENSOR_SERVICE)).getSensorList(Sensor.TYPE_GYROSCOPE).size() == 0)
        {
            new AlertDialog.Builder(this)
                .setCancelable(false) // This blocks the 'BACK' button
                .setTitle(getResources().getString(R.string.gyroscope_missing))
                .setMessage(getResources().getString(R.string.gyroscope_missing_message))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
        }

        requestPermissions();

        if (Globals.globalConfigs == null) {
            Globals.globalConfigs = new Configs(this);
        }

        if (Globals.appDB == null) {
            Globals.appDB = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "osmCacheDb").build();
        }

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.isFirstRun)) {
            AlertDialog d = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(getResources().getString(R.string.first_run, getResources().getString(R.string.app_name)))
                    .setMessage(Html.fromHtml(getResources().getString(R.string.first_run_message, getResources().getString(R.string.app_name))))
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNeutralButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Globals.globalConfigs.setBoolean(Configs.ConfigKey.isFirstRun, false);
                        }
                    })
                    .show();

            d.setCanceledOnTouchOutside(false);
            ((TextView) d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        }

        initializeGlobals();
    }

    private void initializeGlobals() {
        Globals.baseContext = this.getBaseContext();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Globals.onResume(this);
    }

    @Override
    protected void onPause() {
        Globals.onPause(this);

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
                if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.useArCore)) {
                    intent = new Intent(MainActivity.this, ViewTopoArCoreActivity.class);
                    startActivity(intent);
                } else {
                    intent = new Intent(MainActivity.this, ViewTopoActivity.class);
                    startActivity(intent);
                }
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
}
