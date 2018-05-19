package com.ar.climbing.activitys;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.ar.climbing.R;
import com.ar.climbing.sensors.ILocationListener;
import com.ar.climbing.sensors.IOrientationListener;
import com.ar.climbing.sensors.LocationHandler;
import com.ar.climbing.sensors.SensorListener;
import com.ar.climbing.storage.AsyncDataManager;
import com.ar.climbing.storage.IDataManagerEventListener;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.DialogBuilder;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.widgets.CompassWidget;
import com.ar.climbing.widgets.MapViewWidget;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, IDataManagerEventListener {
    private MapViewWidget mapWidget;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;

    private FolderOverlay tapMarkersFolder = new FolderOverlay();
    private Marker tapMarker;
    private AsyncDataManager downloadManager;
    private Map<Long, GeoNode> allPOIs = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));

        mapWidget = new MapViewWidget(this, findViewById(R.id.mapViewContainer), allPOIs, tapMarkersFolder);
        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);
        mapWidget.setAllowAutoCenter(false);
        mapWidget.centerOnObserver();

        initTapMarker();

        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < Constants.ON_TAP_DELAY_MS)) {
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());
                    tapMarker.setPosition(gp);
                }
                return false;
            }
        });

        mapWidget.getOsmMap().addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                updatePOIs(false);
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updatePOIs(false);
                return false;
            }
        }));

        this.downloadManager = new AsyncDataManager(true);
        downloadManager.addObserver(this);

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE),
                ViewMapActivity.this, this);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);
    }

    private void updatePOIs(boolean cleanState) {
        if (cleanState) {
            allPOIs.clear();
        }

        downloadManager.loadBBox(mapWidget.getOsmMap().getBoundingBox(), allPOIs);
    }

    public void onSettingsButtonClick (View v) {
        Intent intent = new Intent(ViewMapActivity.this, SettingsActivity.class);
        startActivityForResult(intent, Constants.OPEN_CONFIG_ACTIVITY);
    }

    @Override
    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        Globals.virtualCamera.degAzimuth = pAzimuth;
        Globals.virtualCamera.degPitch = pPitch;
        Globals.virtualCamera.degRoll = pRoll;

        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationHandler.onResume();
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_UI);

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        updatePOIs(false);
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        sensorManager.unregisterListener(sensorListener);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        DialogBuilder.buildObserverInfoDialog(v);
    }

    public void onCreateButtonClick (View v) {
        Intent intent = new Intent(this, EditTopoActivity.class);
        intent.putExtra("poiLat", tapMarker.getPosition().getLatitude());
        intent.putExtra("poiLon", tapMarker.getPosition().getLongitude());
        startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
            updatePOIs(true);
        }

        if (requestCode == Constants.OPEN_CONFIG_ACTIVITY) {
            updatePOIs(true);
        }
    }

    private void initTapMarker() {
        List<Overlay> list = tapMarkersFolder.getItems();

        list.clear();

        Drawable nodeIcon = getResources().getDrawable(R.drawable.center);

        tapMarker = new Marker(mapWidget.getOsmMap());
        tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        tapMarker.setIcon(nodeIcon);
        tapMarker.setImage(nodeIcon);
        tapMarker.setInfoWindow(null);
        tapMarker.setPosition(Globals.poiToGeoPoint(Globals.virtualCamera));

        //put into FolderOverlay list
        list.add(tapMarker);
    }

    @Override
    public void onProgress(int progress, boolean hasChanges,  Map<String, Object> parameters) {
        if (progress == 100 && (hasChanges || allPOIs.isEmpty())) {
            mapWidget.resetPOIs();
        }
    }
}

