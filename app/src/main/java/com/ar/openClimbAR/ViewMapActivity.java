package com.ar.openClimbAR;

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

import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.tools.ILocationListener;
import com.ar.openClimbAR.tools.IOrientationListener;
import com.ar.openClimbAR.tools.PointOfInterestDialogBuilder;
import com.ar.openClimbAR.utils.GlobalVariables;
import com.ar.openClimbAR.utils.MapViewWidget;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {

    private static final int OPEN_NEW_ACTIVITY = (ViewMapActivity.class.hashCode()& 0x0000ffff);
    private MapViewWidget mapWidget;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;
    private View compass;

    private FolderOverlay tapMarkersFolder = new FolderOverlay();
    private Marker tapMarker;
    private View newTopo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        this.compass = findViewById(R.id.compassView);
        this.newTopo = findViewById(R.id.createButton);

        mapWidget = new MapViewWidget((MapView) findViewById(R.id.openMapView), GlobalVariables.allPOIs, tapMarkersFolder);
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

                    newTopo.setVisibility(View.VISIBLE);
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

    public void onCreateButtonClick (View v) {
        Intent intent = new Intent(this, EditTopoActivity.class);
        intent.putExtra("poiID", -1l);
        intent.putExtra("poiLat", tapMarker.getPosition().getLatitude());
        intent.putExtra("poiLon", tapMarker.getPosition().getLongitude());
        startActivityForResult(intent, OPEN_NEW_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OPEN_NEW_ACTIVITY) {
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

        //put into FolderOverlay list
        list.add(tapMarker);
    }
}

