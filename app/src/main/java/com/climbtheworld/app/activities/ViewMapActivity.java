package com.climbtheworld.app.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.climbtheworld.app.R;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationHandler;
import com.climbtheworld.app.sensors.SensorListener;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import needle.CancelableTask;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {
    private MapViewWidget mapWidget;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;

    private FolderOverlay tapMarkersFolder = new FolderOverlay();
    private Marker tapMarker;
    private DataManager downloadManager;
    private Map<Long, MarkerGeoNode> allPOIs = new LinkedHashMap<>();

    private CancelableTask dbTask = null;

    private static final int locationUpdate = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));

        mapWidget = new MapViewWidget(this, findViewById(R.id.mapViewContainer), allPOIs, tapMarkersFolder);
        mapWidget.setShowObserver(true, null);
        mapWidget.setMapAutoFollow(true);
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

        mapWidget.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                updatePOIs(true);
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updatePOIs(true);
                return false;
            }
        }));

        this.downloadManager = new DataManager(true);

        //location
        locationHandler = new LocationHandler(ViewMapActivity.this, this, locationUpdate);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);
    }

    private void updatePOIs(final boolean cleanState) {
        final BoundingBox bBox = mapWidget.getOsmMap().getBoundingBox();

        if (dbTask != null) {
            dbTask.cancel();
        }

        dbTask = new CancelableTask() {
            @Override
            protected void doWork() {
                if (cleanState && !isCanceled()) {
                    allPOIs.clear();
                }

                boolean result = downloadManager.loadBBox(bBox, allPOIs);
                if ((result || allPOIs.isEmpty())  && !isCanceled()) {
                    mapWidget.resetPOIs();
                }
            }
        };

        Constants.DB_EXECUTOR
                .execute(dbTask);
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

        mapWidget.onOrientationChange(pAzimuth, pPitch, pRoll);
        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.onLocationChange();
        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Globals.onResume(this);
        locationHandler.onResume();

        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);

        findViewById(R.id.mapViewContainer).post(new Runnable() {
            @Override
            public void run() {
                updatePOIs(true);
            }
        });
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        sensorManager.unregisterListener(sensorListener);

        Globals.onPause(this);
        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        DialogBuilder.buildObserverInfoDialog(v).show();
    }

    public void onCreateButtonClick (View v) {
        Intent intent = new Intent(this, EditNodeActivity.class);
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

        Drawable nodeIcon = getResources().getDrawable(R.drawable.ic_center);

        tapMarker = new Marker(mapWidget.getOsmMap());
        tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        tapMarker.setIcon(nodeIcon);
        tapMarker.setImage(nodeIcon);
        tapMarker.setInfoWindow(null);
        tapMarker.setPosition(Globals.poiToGeoPoint(Globals.virtualCamera));

        //put into FolderOverlay list
        list.add(tapMarker);
    }
}

