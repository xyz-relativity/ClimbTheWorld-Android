package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.climbtheworld.app.BuildConfig;
import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.config.Configuration;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

public class MainActivity extends AppCompatActivity {
    int importCounter = ImporterActivity.IMPORT_COUNTER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // This call has to be the first call of the application
        initializeGlobals();

        String version = "";
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            version = getString(R.string.version, pInfo.versionName);
            ((TextView)findViewById(R.id.textVersionString)).setText(version);
        } catch (PackageManager.NameNotFoundException ignore) {
        }

        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            List<String> segments = data.getPathSegments();
            if (segments.get(0).equalsIgnoreCase("location") && segments.size() == 2) {
                Intent mapIntent = new Intent(this, ViewMapActivity.class);
                mapIntent.putExtra("GeoPoint", segments.get(1));
                this.startActivity(mapIntent);
            }
        }

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

        if (Configs.instance(this).getBoolean(Configs.ConfigKey.isFirstRun)) {
            Globals.showDownloadPopup = false;
            Intent firstRunIntent = new Intent(MainActivity.this, FirstRunActivity.class);
            startActivity(firstRunIntent);
        }
    }

    private synchronized void initializeGlobals() {
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
        importCounter = ImporterActivity.IMPORT_COUNTER;
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
                intent = new Intent(this, AugmentedRealityActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonViewMap:
                intent = new Intent(this, ViewMapActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonTools:
                intent = new Intent(this, ToolsActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonDonate:
                intent = new Intent(this, SupportMeActivity.class);
                startActivity(intent);
                break;

            case R.id.textVersionString:
                importCounter--;
                if (importCounter <= 0) {
                    intent = new Intent(this, ImporterActivity.class);
                    startActivity(intent);
                    importCounter = ImporterActivity.IMPORT_COUNTER;
                }
                break;
        }
    }
}
