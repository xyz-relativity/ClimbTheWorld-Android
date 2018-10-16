package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.arch.persistence.room.Room;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.climbtheworld.app.BuildConfig;
import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.config.Configuration;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // This call has to be the first call of the application
        initializeGlobals();

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
        Globals.requestPermissions(this);

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.isFirstRun)) {
            Intent intent = new Intent(MainActivity.this, FirstRunActivity.class);
            startActivity(intent);
        }
    }

    private void initializeGlobals() {
        Globals.baseContext = this.getBaseContext();

        if (Globals.globalConfigs == null) {
            Globals.globalConfigs = new Configs(this);
        }

        if (Globals.appDB == null) {
            Globals.appDB = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "osmCacheDb")
                    .addMigrations(AppDatabase.MIGRATION_1_2).build();
        }
        //use private storage for ASM cache to avoid the need for external storage permissions.
        Configuration.getInstance().setOsmdroidTileCache(getFilesDir().getAbsoluteFile());
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

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
