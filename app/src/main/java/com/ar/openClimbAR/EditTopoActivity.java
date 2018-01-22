package com.ar.openClimbAR;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.ILocationListener;
import com.ar.openClimbAR.tools.IOrientationListener;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterestDialogBuilder;
import com.ar.openClimbAR.utils.Constants;
import com.ar.openClimbAR.utils.GlobalVariables;
import com.ar.openClimbAR.utils.MapViewWidget;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EditTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {
    private PointOfInterest poi;
    private Long poiID;
    private MapViewWidget mapWidget;
    private LocationHandler locationHandler;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private View compass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topo);

        this.compass = findViewById(R.id.compassView);

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE), EditTopoActivity.this, this);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this);

        Intent intent = getIntent();
        poiID = intent.getLongExtra("poiID", -1);
        poi = GlobalVariables.allPOIs.get(poiID);

        Map<Long, PointOfInterest> poiMap = new ConcurrentHashMap<>();
        poiMap.put(poiID, poi);

        mapWidget = new MapViewWidget((MapView) findViewById(R.id.openMapView), poiMap);
        mapWidget.setShowPoiInfoDialog(false);
        mapWidget.setAllowAutoCenter(false);
        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < 150)) {
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());

                    ((EditText)findViewById(R.id.editLatitude)).setText(String.format(Locale.getDefault(), "%f", (float) gp.getLatitude()));
                    ((EditText)findViewById(R.id.editLongitude)).setText(String.format(Locale.getDefault(), "%f", (float) gp.getLongitude()));
                    updatePoi();
                    updateMapMarker();
                    return true;
                }
                return false;
            }
        });

        mapWidget.getOsmMap().getController().setCenter(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));

        ((EditText)findViewById(R.id.editTopoName)).setText(poi.name);
        ((EditText)findViewById(R.id.editAltitude)).setText(String.format(Locale.getDefault(), "%f", poi.elevationMeters));
        ((EditText)findViewById(R.id.editLength)).setText(String.format(Locale.getDefault(), "%f", poi.getLengthMeters()));
        ((EditText)findViewById(R.id.editDescription)).setText(poi.getDescription());

        ((TextView)findViewById(R.id.grading)).setText(getResources().getString(R.string.grade) + " (" + Constants.DISPLAY_SYSTEM + ")");
        Spinner dropdown = findViewById(R.id.gradeSpinner);
        ArrayList<String> allGrades = GradeConverter.getConverter().getAllGrades(Constants.DISPLAY_SYSTEM);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(poi.getLevelId());

        for (PointOfInterest.climbingStyle style :poi.getClimbingStyles())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null) {
                styleCheckBox.setChecked(true);
            } else {
                System.out.println("No climbing style found: " + style.name());
            }
        }
    }

    public void updatePoi() {
        poi.updatePOILocation(Float.parseFloat(((EditText)findViewById(R.id.editLatitude)).getText().toString()),
                Float.parseFloat(((EditText)findViewById(R.id.editLongitude)).getText().toString()),
                Float.parseFloat(((EditText)findViewById(R.id.editAltitude)).getText().toString()));

        poi.name = ((EditText)findViewById(R.id.editTopoName)).getText().toString();
    }

    public void onClickButtonCancel(View v)
    {
        finish();
    }

    public void onClickButtonSave(View v)
    {
        updatePoi();
        GlobalVariables.allPOIs.put(poiID, poi);
        mapWidget.invalidateCache();
        finish();
    }

    private void updateMapMarker() {
        ((EditText)findViewById(R.id.editLatitude)).setText(String.format(Locale.getDefault(), "%f", poi.decimalLatitude));
        ((EditText)findViewById(R.id.editLongitude)).setText(String.format(Locale.getDefault(), "%f", poi.decimalLongitude));

        mapWidget.invalidateCache();
        mapWidget.invalidate();
    }

    @Override
    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        GlobalVariables.observer.degAzimuth = pAzimuth;
        GlobalVariables.observer.degPitch = pPitch;
        GlobalVariables.observer.degRoll = pRoll;

        compass.setRotation(GlobalVariables.observer.degAzimuth);

        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(float pDecLatitude, float pDecLongitude, float pMetersAltitude, float accuracy) {
        GlobalVariables.observer.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationHandler.onResume();
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        sensorManager.unregisterListener(sensorListener);

        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        PointOfInterestDialogBuilder.obsDialogBuilder(v);
    }
}
