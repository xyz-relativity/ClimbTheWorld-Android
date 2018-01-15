package com.ar.openClimbAR;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.tools.ArUtils;
import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.IEnvironmentHandler;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.utils.Constants;

import org.json.JSONException;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditTopo extends AppCompatActivity implements IEnvironmentHandler {
    private PointOfInterest poi;
    private MapView osmMap;
    private LocationHandler locationHandler;
    private GeoPoint myGPSLocation = null;
    private Marker nodeMarker;
    private Marker locationMarker = null;
    private SensorManager sensorManager;
    private SensorListener sensorListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topo);

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE), EditTopo.this, this, this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener(this);

        osmMap = findViewById(R.id.openMapView);

        Intent intent = getIntent();
        try {
            poi = new PointOfInterest(intent.getStringExtra("poiJSON"));
        } catch (JSONException e) {
            e.printStackTrace();
            finish();
        }

        osmMap.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < 150)) {
                    GeoPoint gp = (GeoPoint) osmMap.getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());
                    poi.updatePOILocation((float) gp.getLatitude(), (float) gp.getLongitude(), (float) gp.getAltitude());
                    updateMapMarker();
                    return true;
                }
                return false;
            }
        });

        //init osm map
        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        osmMap.getController().setZoom(20);
        osmMap.getController().setCenter(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));

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
            ((CheckBox)findViewById(id)).setChecked(true);
        }

        initMarkers();
    }

    public void onClickButtonCancel(View v)
    {
        finish();
    }

    private void initMarkers() {
        FolderOverlay myMarkersFolder = new FolderOverlay();
        List<Overlay> list = myMarkersFolder.getItems();

        Drawable nodeIcon = getResources().getDrawable(R.drawable.marker_default);
        nodeIcon.mutate(); //allow different effects for each marker.

        float remapGradeScale = ArUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.getLevelId());
        nodeIcon.setTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

        nodeMarker = new Marker(osmMap);
        nodeMarker.setAnchor(0.5f, 1f);
        nodeMarker.setPosition(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));
        nodeMarker.setIcon(nodeIcon);
        nodeMarker.setTitle(GradeConverter.getConverter().getGradeFromOrder("UIAA", poi.getLevelId()) +" (UIAA)");
        nodeMarker.setSubDescription(poi.name);
        nodeMarker.setImage(nodeIcon);
        nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
                return true;
            }
        });

        //put into FolderOverlay list
        list.add(nodeMarker);

        nodeIcon = getResources().getDrawable(R.drawable.direction_arrow);
        nodeIcon.mutate(); //allow different effects for each marker.

        locationMarker = new Marker(osmMap);
        locationMarker.setAnchor(0.5f, 0.5f);
        locationMarker.setIcon(nodeIcon);
        locationMarker.setImage(nodeIcon);

        //put into FolderOverlay list
        list.add(locationMarker);

        myMarkersFolder.closeAllInfoWindows();

        osmMap.getOverlays().clear();
        osmMap.getOverlays().add(myMarkersFolder);
        osmMap.invalidate();
    }

    private void updateMapMarker() {
        ((EditText)findViewById(R.id.editLatitude)).setText(String.format(Locale.getDefault(), "%f", poi.decimalLatitude));
        ((EditText)findViewById(R.id.editLongitude)).setText(String.format(Locale.getDefault(), "%f", poi.decimalLongitude));

        nodeMarker.setPosition(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));
        osmMap.invalidate();
    }

    @Override
    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        locationMarker.setRotation(pAzimuth);

        updateMapMarker();
    }

    @Override
    public void updatePosition(float pDecLatitude, float pDecLongitude, float pMetersAltitude, float accuracy) {
        locationMarker.setPosition(new GeoPoint(pDecLatitude, pDecLongitude, pMetersAltitude));

        updateMapMarker();
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationHandler.onResume();
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                sensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        sensorManager.unregisterListener(sensorListener);

        super.onPause();
    }
}
