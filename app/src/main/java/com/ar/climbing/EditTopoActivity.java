package com.ar.climbing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
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

import com.ar.climbing.sensors.LocationHandler;
import com.ar.climbing.sensors.SensorListener;
import com.ar.climbing.tools.GradeConverter;
import com.ar.climbing.utils.CompassWidget;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.ILocationListener;
import com.ar.climbing.utils.IOrientationListener;
import com.ar.climbing.utils.MapViewWidget;
import com.ar.climbing.utils.PointOfInterest;
import com.ar.climbing.utils.PointOfInterestDialogBuilder;

import org.json.JSONException;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EditTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, AdapterView.OnItemSelectedListener {
    private PointOfInterest poi;
    private Long poiID;
    private MapViewWidget mapWidget;
    private LocationHandler locationHandler;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private CompassWidget compass;
    private Spinner dropdown;
    private EditText editTopoName;
    private EditText editElevation;
    private EditText editLength;
    private EditText editDescription;
    private EditText editLatitude;
    private EditText editLongitude;
    private CheckBox checkBoxProtection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topo);

        this.compass = new CompassWidget(findViewById(R.id.compassButton));
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

        Intent intent = getIntent();
        poiID = intent.getLongExtra("poiID", -1);
        PointOfInterest tmpPoi;
        if (poiID == -1) {
            tmpPoi = new PointOfInterest(intent.getDoubleExtra("poiLat", Globals.observer.decimalLatitude),
                    intent.getDoubleExtra("poiLon", Globals.observer.decimalLongitude),
                    Globals.observer.elevationMeters);
        } else {
            tmpPoi = Globals.allPOIs.get(poiID);
        }

        try {
            poi = new PointOfInterest(tmpPoi.toJSONString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<Long, PointOfInterest> poiMap = new ConcurrentHashMap<>();
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

        for (PointOfInterest.ClimbingStyle style: poi.getClimbingStyles())
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

        List<PointOfInterest.ClimbingStyle> styles = new ArrayList<>();
        for (PointOfInterest.ClimbingStyle style: PointOfInterest.ClimbingStyle.values())
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

    public void onClickButtonCancel(View v)
    {
        finish();
    }

    public void onClickButtonSave(View v)
    {
        updatePoi();
        Globals.allPOIs.put(poiID, poi);
        finish();
    }

    public void onClickButtonDelete(View v)
    {
        updatePoi();

        new AlertDialog.Builder(this)
                .setTitle(String.format(getResources().getString(R.string.delete_confirmation) ,poi.getName()))
                .setMessage(R.string.delete_confirmation_message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        Globals.allPOIs.remove(poiID);
                        finish();
                    }})
                .setNegativeButton(android.R.string.no, null).show();
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
        PointOfInterestDialogBuilder.obsDialogBuilder(v);
    }
}