package com.ar.openClimbAR;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.tools.ILocationListener;
import com.ar.openClimbAR.tools.IOrientationListener;
import com.ar.openClimbAR.utils.Constants;
import com.ar.openClimbAR.utils.GlobalVariables;
import com.ar.openClimbAR.utils.MapUtils;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {

    private MapView osmMap;
    private final FolderOverlay myMarkersFolder = new FolderOverlay();
    private Marker locationMarker;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        osmMap = findViewById(R.id.openMapView);

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE),
                ViewMapActivity.this, this, this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener(this);

        //init osm map
        osmMap.setBuiltInZoomControls(true);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.OpenTopo);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL - 6);
        osmMap.setMaxZoomLevel(Constants.MAP_MAX_ZOOM_LEVEL);

        locationMarker = MapUtils.initMyLocationMarkers(osmMap, myMarkersFolder);
        locationMarker.setPosition(new GeoPoint(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude));

        osmMap.getController().setCenter(locationMarker.getPosition());

        for (long poiID : GlobalVariables.allPOIs.keySet()) {
            MapUtils.addMapMarker(GlobalVariables.allPOIs.get(poiID), osmMap, myMarkersFolder);
        }
    }

    public void onClickButtonCenterMap(View v)
    {
        osmMap.getController().setCenter(locationMarker.getPosition());
    }

    @Override
    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        locationMarker.setRotation(pAzimuth);
        osmMap.invalidate();
    }

    @Override
    public void updatePosition(float pDecLatitude, float pDecLongitude, float pMetersAltitude, float accuracy) {
        GlobalVariables.observer.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
        locationMarker.setPosition(new GeoPoint(pDecLatitude, pDecLongitude, pMetersAltitude));
        osmMap.invalidate();
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
}

