package com.ar.climbing;

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

import com.ar.climbing.sensors.LocationHandler;
import com.ar.climbing.sensors.SensorListener;
import com.ar.climbing.utils.CompassWidget;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.ILocationListener;
import com.ar.climbing.utils.IOrientationListener;
import com.ar.climbing.utils.MapViewWidget;
import com.ar.climbing.utils.PointOfInterestDialogBuilder;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {
    private MapViewWidget mapWidget;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;
    private CompassWidget compass;

    private FolderOverlay tapMarkersFolder = new FolderOverlay();
    private Marker tapMarker;
    private View newTopo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        this.compass = new CompassWidget(findViewById(R.id.compassButton));
        this.newTopo = findViewById(R.id.createButton);

        mapWidget = new MapViewWidget(this, (MapView) findViewById(R.id.openMapView), Globals.allPOIs, tapMarkersFolder);
        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);
        mapWidget.setAllowAutoCenter(false);
        mapWidget.setMapTileSource(TileSourceFactory.OpenTopo);
        mapWidget.centerOnObserver();
        initTapMarker();

        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < 150)) {
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());
                    tapMarker.setPosition(gp);

                    return false;
                }
                return false;
            }
        });

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE),
                ViewMapActivity.this, this);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);
    }

    public void onClickButtonCenterMap(View v)
    {
        mapWidget.centerOnObserver();
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

    public void onCreateButtonClick (View v) {
        Intent intent = new Intent(this, EditTopoActivity.class);
        intent.putExtra("poiID", -1l);
        intent.putExtra("poiLat", tapMarker.getPosition().getLatitude());
        intent.putExtra("poiLon", tapMarker.getPosition().getLongitude());
        startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
            mapWidget.resetPOIs();
            mapWidget.invalidate();
        }
    }

    private void initTapMarker() {
        List<Overlay> list = tapMarkersFolder.getItems();

        list.clear();

        Drawable nodeIcon = getResources().getDrawable(R.drawable.center);
        nodeIcon.mutate(); //allow different effects for each marker.

        tapMarker = new Marker(mapWidget.getOsmMap());
        tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        tapMarker.setIcon(nodeIcon);
        tapMarker.setImage(nodeIcon);
        tapMarker.setInfoWindow(null);
        tapMarker.setPosition(Globals.poiToGeoPoint(Globals.observer));

        //put into FolderOverlay list
        list.add(tapMarker);
    }
}
