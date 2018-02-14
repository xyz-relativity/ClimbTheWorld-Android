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
import com.ar.climbing.storage.database.AppDatabase;
import com.ar.climbing.storage.database.Node;
import com.ar.climbing.tools.GradeConverter;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.PointOfInterest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;

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

        initPoiFromDB();
//        initPoiFromResources();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Globals.globalConfigs.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }

    private void initPoiFromDB() {
        (new Thread() {
            public void run() {
                List<Node> nodes = Globals.appDB.nodeDao().loadAllNodes();

                try {
                    for (Node i : nodes) {
                        Globals.allPOIs.put(i.osmID, new PointOfInterest(i.nodeInfo));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void initPoiFromResources() {
        InputStream is = getResources().openRawResource(R.raw.world_db);

        if (is == null) {
            return;
        }

        BufferedReader reader = null;
        reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        String line = "";
        try {
            StringBuilder responseStrBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                responseStrBuilder.append(line);
            }

            parseNodesFromJSON(responseStrBuilder.toString());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return;
        }

        return;
    }

    private void parseNodesFromJSON(String data) throws JSONException {
        JSONObject jObject = new JSONObject(data);
        JSONArray jArray = jObject.getJSONArray("elements");

        for (int i=0; i < jArray.length(); i++) {
            JSONObject nodeInfo = jArray.getJSONObject(i);
            //open street maps ID should be unique since it is a DB ID.
            long nodeID = nodeInfo.getLong("id");
            if (Globals.allPOIs.containsKey(nodeID)) {
                continue;
            }

            PointOfInterest tmpPoi = new PointOfInterest(nodeInfo);
            Globals.allPOIs.put(nodeID, tmpPoi);
        }
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
