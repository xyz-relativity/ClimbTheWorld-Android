package com.climbtheworld.app.activities;

import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.sensors.LocationHandler;
import com.climbtheworld.app.sensors.SensorListener;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.widgets.MapViewWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ViewTopoArCoreActivity extends AppCompatActivity {

    private GLSurfaceView arGearView;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;

    private Map<Long, MarkerGeoNode> boundingBoxPOIs = new HashMap<>(); //POIs around the virtualCamera.

    private MapViewWidget mapWidget;
    private AugmentedRealityViewManager viewManager;
    private DataManager downloadManager;

    private CountDownTimer gpsUpdateAnimationTimer;
    private double maxDistance;

    private List<GeoNode> visible = new ArrayList<>();
    private List<GeoNode> zOrderedDisplay = new ArrayList<>();
    private Map<Long, MarkerGeoNode> allPOIs = new LinkedHashMap<>();
    private AtomicBoolean updatingView = new AtomicBoolean();

    private final int locationUpdate = 500;

    // documentation: https://developers.google.com/ar/develop/java/emulator

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo_ar_core);
    }
}
