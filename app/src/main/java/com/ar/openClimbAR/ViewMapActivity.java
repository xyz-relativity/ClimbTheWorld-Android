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
import com.ar.openClimbAR.utils.GlobalVariables;
import com.ar.openClimbAR.utils.MapViewWidget;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {

    private MapViewWidget mapWidget;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        mapWidget = new MapViewWidget((MapView) findViewById(R.id.openMapView), GlobalVariables.allPOIs);
        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);
        mapWidget.setAllowAutoCenter(false);
        mapWidget.setMapTileSource(TileSourceFactory.OpenTopo);
        mapWidget.centerOnObserver();

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE),
                ViewMapActivity.this, this);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this);
    }

    public void onClickButtonCenterMap(View v)
    {
        mapWidget.centerOnObserver();
    }

    @Override
    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        GlobalVariables.observer.degAzimuth = pAzimuth;
        GlobalVariables.observer.degPitch = pPitch;
        GlobalVariables.observer.degRoll = pRoll;

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
}

