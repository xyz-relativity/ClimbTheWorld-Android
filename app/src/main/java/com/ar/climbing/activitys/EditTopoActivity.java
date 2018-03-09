package com.ar.climbing.activitys;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.sensors.LocationHandler;
import com.ar.climbing.sensors.SensorListener;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.tools.GradeConverter;
import com.ar.climbing.utils.CompassWidget;
import com.ar.climbing.utils.GeoNodeDialogBuilder;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.ILocationListener;
import com.ar.climbing.utils.IOrientationListener;
import com.ar.climbing.utils.MapViewWidget;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class EditTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, AdapterView.OnItemSelectedListener {
    private GeoNode poi;
    private Long poiID;
    private MapViewWidget mapWidget;
    private LocationHandler locationHandler;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private Spinner dropdown;
    private EditText editTopoName;
    private EditText editElevation;
    private EditText editLength;
    private EditText editDescription;
    private EditText editLatitude;
    private EditText editLongitude;
    private CheckBox checkBoxProtection;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topo);

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        this.editTopoName = findViewById(R.id.editTopoName);
        this.editElevation = findViewById(R.id.editElevation);
        this.editLength = findViewById(R.id.editLength);
        this.editDescription = findViewById(R.id.editDescription);
        this.editLatitude = findViewById(R.id.editLatitude);
        this.editLongitude = findViewById(R.id.editLongitude);
        this.checkBoxProtection = findViewById(R.id.bolted);
        this.dropdown = findViewById(R.id.gradeSpinner);

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE), EditTopoActivity.this, this);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);

        intent = getIntent();
        AsyncTask task = new BdLoad().execute(intent);
        try {
            poi = (GeoNode)task.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        poiID = poi.getID();

        Map<Long, GeoNode> poiMap = new HashMap<>();
        poiMap.put(poiID, poi);

        mapWidget = new MapViewWidget(this, (MapView) findViewById(R.id.openMapView), poiMap);
        mapWidget.setShowPoiInfoDialog(false);
        mapWidget.setAllowAutoCenter(false);
        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < 150)) {
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());

                    poi.updatePOILocation(gp.getLatitude(), gp.getLongitude(), poi.elevationMeters);
                    updateMapMarker();

                    return true;
                }
                return false;
            }
        });

        mapWidget.getOsmMap().getController().setCenter(Globals.poiToGeoPoint(poi));
        updateMapMarker();

        editTopoName.setText(poi.getName());
        editElevation.setText(String.format(Locale.getDefault(), "%f", poi.elevationMeters));
        editLength.setText(String.format(Locale.getDefault(), "%f", poi.getLengthMeters()));
        editDescription.setText(poi.getDescription());

        ((TextView)findViewById(R.id.grading)).setText(getResources().getString(R.string.grade) + " (" + Globals.globalConfigs.getDisplaySystem() + ")");

        dropdown.setOnItemSelectedListener(this);
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getDisplaySystem());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(poi.getLevelId());

        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null) {
                styleCheckBox.setChecked(true);
            }
        }

        checkBoxProtection.setChecked(poi.isBolted());
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        poi.setLevelFromID(pos);
        updateMapMarker();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }


    public void updatePoi() {
        poi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                Double.parseDouble(editLongitude.getText().toString()),
                Double.parseDouble(editElevation.getText().toString()));

        poi.setName(editTopoName.getText().toString());
        poi.setDescription(editDescription.getText().toString());
        poi.setLengthMeters(Double.parseDouble(editLength.getText().toString()));

        List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }
        poi.setClimbingStyles(styles);
        poi.setLevelFromID(dropdown.getSelectedItemPosition());
        poi.setBolted(checkBoxProtection.isChecked());
    }

    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.ButtonCancel:
                finish();
                break;

            case R.id.ButtonSave:
                updatePoi();
                poi.updateDate = System.currentTimeMillis();
                poi.localUpdateStatus = GeoNode.TO_UPDATE_STATE;
                new BdPush().execute(poi);
                finish();
                break;

            case R.id.ButtonDelete:
                new AlertDialog.Builder(this)
                        .setTitle(getResources().getString(R.string.delete_confirmation ,poi.getName()))
                        .setMessage(R.string.delete_confirmation_message)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                poi.updateDate = System.currentTimeMillis();
                                poi.localUpdateStatus = GeoNode.TO_DELETE_STATE;
                                new BdPush().execute(poi);
                                finish();
                            }})
                        .setNegativeButton(android.R.string.no, null).show();
                break;
        }
    }

    private void updateMapMarker() {
        editLatitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLongitude));

        mapWidget.resetPOIs();
        mapWidget.invalidate();
    }

    @Override
    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        Globals.observer.degAzimuth = pAzimuth;
        Globals.observer.degPitch = pPitch;
        Globals.observer.degRoll = pRoll;

        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.observer.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationHandler.onResume();
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);

        if (Globals.globalConfigs.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        sensorManager.unregisterListener(sensorListener);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        GeoNodeDialogBuilder.obsDialogBuilder(v);
    }

    private static class BdLoad extends AsyncTask<Intent, Void, GeoNode> {
        @Override
        protected GeoNode doInBackground(Intent... intents) {
            Intent intent = intents[0];

            long poiID = intent.getLongExtra("poiID", -1);
            if (poiID == -1) {
                GeoNode tmpPoi = new GeoNode(intent.getDoubleExtra("poiLat", Globals.observer.decimalLatitude),
                        intent.getDoubleExtra("poiLon", Globals.observer.decimalLongitude),
                        Globals.observer.elevationMeters);

                poiID = Globals.appDB.nodeDao().getSmallestId();
                if (poiID >= 0) {
                    poiID = -1l;
                } else {
                    poiID -= 1;
                }
                tmpPoi.osmID = poiID;

                return tmpPoi;
            } else {
                return Globals.appDB.nodeDao().loadNode(poiID);
            }
        }
    }

    private static class BdPush extends AsyncTask<GeoNode, Void, Void> {
        @Override
        protected Void doInBackground(GeoNode... nodes) {
            Globals.appDB.nodeDao().insertNodesWithReplace(nodes);
            return null;
        }
    }
}
