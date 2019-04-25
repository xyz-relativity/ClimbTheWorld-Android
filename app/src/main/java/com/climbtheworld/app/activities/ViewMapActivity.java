package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationManager;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.climbtheworld.app.widgets.MapWidgetFactory;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import needle.UiRelatedTask;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {
    private MapViewWidget mapWidget;
    private OrientationManager orientationManager;
    private LocationManager locationManager;
    private View loading;

    private FolderOverlay tapMarkersFolder = new FolderOverlay();
    private Marker tapMarker;
    private DataManager downloadManager;
    private ConcurrentHashMap<Long, MarkerGeoNode> allPOIs = new ConcurrentHashMap<>();

    private UiRelatedTask dbTask = null;

    private static final int locationUpdate = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        Ask.on(this)
                .id(500) // in case you are invoking multiple time Ask from same activity or fragment
                .forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .withRationales(getString(R.string.map_location_rational)) //optional
                .go();

        loading = findViewById(R.id.mapLoadingIndicator);
        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));

        mapWidget = MapWidgetFactory.buildMapView(this, tapMarkersFolder);
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

        Intent intent = getIntent();
        if (intent.hasExtra("GeoPoint")) {
            GeoPoint location = GeoPoint.fromDoubleString(intent.getStringExtra("GeoPoint"), ',');
            Double zoom = intent.getDoubleExtra("zoom", mapWidget.getMaxZoomLevel());
            centerOnLocation(location, zoom);
            mapWidget.setMapAutoFollow(false);
        } else {
            mapWidget.setMapAutoFollow(true);
        }

        this.downloadManager = new DataManager(true);

        //location
        locationManager = new LocationManager(ViewMapActivity.this, this, locationUpdate);
        locationManager.addListener(this);

        orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_NORMAL);
        orientationManager.addListener(this, compass);
    }

    private void updatePOIs(final boolean cleanState) {
        final BoundingBox bBox = mapWidget.getOsmMap().getBoundingBox();

        loading.setVisibility(View.VISIBLE);

        if (dbTask != null) {
            dbTask.cancel();
        }

        dbTask = new UiRelatedTask() {
            @Override
            protected Object doWork() {
                if (cleanState && !isCanceled()) {
                    allPOIs.clear();
                }

                boolean result = downloadManager.loadBBox(bBox, allPOIs);
                return (result || allPOIs.isEmpty()) && !isCanceled();
            }

            @Override
            protected void thenDoUiRelatedWork(Object o) {
                loading.setVisibility(View.GONE);
                if ((boolean)o) {
                    mapWidget.resetPOIs(new ArrayList<MapViewWidget.MapMarkerElement>(allPOIs.values()));
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
        mapWidget.onOrientationChange(pAzimuth, pPitch, pRoll);
        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.onLocationChange(Globals.poiToGeoPoint(Globals.virtualCamera));
        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Globals.onResume(this);
        mapWidget.onResume();

        locationManager.onResume();
        orientationManager.onResume();

        findViewById(R.id.mapViewContainer).post(new Runnable() {
            @Override
            public void run() {
                updatePOIs(true);
            }
        });
    }

    @Override
    protected void onPause() {
        locationManager.onPause();
        orientationManager.onPause();

        Globals.onPause(this);
        mapWidget.onPause();

        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        DialogBuilder.buildObserverInfoDialog(this, orientationManager).show();
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

    public void centerOnLocation (GeoPoint location) {
        centerOnLocation(location, mapWidget.getMaxZoomLevel());
    }

    public void centerOnLocation (GeoPoint location, Double zoom) {
        mapWidget.setMapAutoFollow(false);
        mapWidget.centerOnGoePoint(location, zoom);
    }
}

