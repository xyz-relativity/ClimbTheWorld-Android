package com.ar.climbing.activitys;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.ar.climbing.R;
import com.ar.climbing.augmentedreality.AugmentedRealityViewManager;
import com.ar.climbing.sensors.LocationHandler;
import com.ar.climbing.sensors.SensorListener;
import com.ar.climbing.sensors.camera.AutoFitTextureView;
import com.ar.climbing.sensors.camera.CameraHandler;
import com.ar.climbing.sensors.camera.CameraTextureViewListener;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.storage.AsyncDataManager;
import com.ar.climbing.storage.IDataManagerEventListener;
import com.ar.climbing.augmentedreality.AugmentedRealityUtils;
import com.ar.climbing.utils.CompassWidget;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.GeoNodeDialogBuilder;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.ILocationListener;
import com.ar.climbing.utils.IOrientationListener;
import com.ar.climbing.utils.MapViewWidget;
import com.ar.climbing.utils.Quaternion;
import com.ar.climbing.utils.Vector2d;

import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ViewTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, IDataManagerEventListener {

    private static final int POS_UPDATE_ANIMATION_STEPS = 10;

    private AutoFitTextureView textureView;
    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;
    private View horizon;

    private Map<Long, GeoNode> boundingBoxPOIs = new ConcurrentHashMap<>(); //POIs around the observer.

    private MapViewWidget mapWidget;
    private AugmentedRealityViewManager viewManager;
    private AsyncDataManager downloadManager;

    private CountDownTimer gpsUpdateAnimationTimer;
    private double maxDistance;

    private List<GeoNode> visible = new ArrayList<>();
    private List<GeoNode> zOrderedDisplay = new ArrayList<>();
    private Map<Long, GeoNode> allPOIs = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo);

        //others
        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        this.viewManager = new AugmentedRealityViewManager(this);
        this.mapWidget = new MapViewWidget(this, (MapView)findViewById(R.id.openMapView), allPOIs);
        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);
        mapWidget.getOsmMap().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                downloadManager.loadBBox(mapWidget.getOsmMap().getBoundingBox(), allPOIs);
            }
        });

        this.horizon = findViewById(R.id.horizon);

        horizon.getLayoutParams().width = (int)Math.sqrt((viewManager.rotateDisplaySize.x * viewManager.rotateDisplaySize.x)
                + (viewManager.rotateDisplaySize.y * viewManager.rotateDisplaySize.y));

        this.downloadManager = new AsyncDataManager();
        downloadManager.addObserver(this);

        //camera
        this.textureView = findViewById(R.id.texture);
        assert textureView != null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
                    ViewTopoActivity.this, this, textureView);
            cameraTextureListener = new CameraTextureViewListener(camera);
            textureView.setSurfaceTextureListener(cameraTextureListener);
        }

        Globals.observer.fieldOfViewDeg = camera.getDegFOV();
        Globals.observer.screenRotation = Globals.getScreenRotationAngle(getWindowManager().getDefaultDisplay().getRotation());

        //location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationHandler = new LocationHandler(locationManager, ViewTopoActivity.this, this);
        locationHandler.addListener(this);

        //orientation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);
        maxDistance = Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);
    }

    public void onCompassButtonClick (View v) {
        GeoNodeDialogBuilder.buildObserverInfoDialog(v);
    }

    public void onSettingsButtonClick (View v) {
        Intent intent = new Intent(ViewTopoActivity.this, SettingsActivity.class);
        startActivityForResult(intent, Constants.OPEN_CONFIG_ACTIVITY);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CameraHandler.REQUEST_CAMERA_PERMISSION || requestCode == LocationHandler.REQUEST_FINE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(ViewTopoActivity.this, "Sorry!!!, you can't use this app without granting permission",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera.startBackgroundThread();
            if (textureView.isAvailable()) {
                camera.openCamera(textureView.getWidth(), textureView.getHeight());
            } else {
                textureView.setSurfaceTextureListener(cameraTextureListener);
            }
        }

        locationHandler.onResume();

        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_FASTEST);

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (!Globals.globalConfigs.getBoolean(Configs.ConfigKey.showVirtualHorizon)) {
            horizon.setVisibility(View.INVISIBLE);
        }

        updatePosition(Globals.observer.decimalLatitude, Globals.observer.decimalLongitude, Globals.observer.elevationMeters, 10);
    }

    @Override
    protected void onPause() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera.closeCamera();
            camera.stopBackgroundThread();
        }

        sensorManager.unregisterListener(sensorListener);
        locationHandler.onPause();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        horizon.setVisibility(View.VISIBLE);

        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
            recreate(); //reset the current activity
        }

        if (requestCode == Constants.OPEN_CONFIG_ACTIVITY) {
            recreate(); //reset the current activity
        }
    }

    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        Globals.observer.degAzimuth = pAzimuth;
        Globals.observer.degPitch = pPitch;
        Globals.observer.degRoll = pRoll;
        Globals.observer.fieldOfViewDeg = camera.getDegFOV();

        updateView();
    }

    public void updatePosition(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double accuracy) {
        final int animationInterval = 100;

        loadPOIs(pDecLatitude, pDecLongitude, pMetersAltitude);

        if (gpsUpdateAnimationTimer != null)
        {
            gpsUpdateAnimationTimer.cancel();
        }

        //Do a nice animation when moving to a new GPS position.
        gpsUpdateAnimationTimer = new CountDownTimer(Math.min(LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL, animationInterval * POS_UPDATE_ANIMATION_STEPS)
                , animationInterval) {
            public void onTick(long millisUntilFinished) {
                long numSteps = millisUntilFinished / animationInterval;
                if (numSteps != 0) {
                    double xStepSize = (pDecLongitude - Globals.observer.decimalLongitude) / numSteps;
                    double yStepSize = (pDecLatitude - Globals.observer.decimalLatitude) / numSteps;

                    Globals.observer.updatePOILocation(Globals.observer.decimalLatitude + yStepSize,
                            Globals.observer.decimalLongitude + xStepSize, pMetersAltitude);
                    updateBoundingBox(Globals.observer.decimalLatitude, Globals.observer.decimalLongitude, Globals.observer.elevationMeters);
                }
            }

            public void onFinish() {
                Globals.observer.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
                updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
            }
        }.start();
    }

    private void updateBoundingBox(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude) {
        double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
        double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

        for (Long poiID: allPOIs.keySet()) {
            GeoNode poi = allPOIs.get(poiID);
            if ((poi.decimalLatitude > pDecLatitude - deltaLatitude && poi.decimalLatitude < pDecLatitude + deltaLatitude)
                    && (poi.decimalLongitude > pDecLongitude - deltaLongitude && poi.decimalLongitude < pDecLongitude + deltaLongitude)) {

                boundingBoxPOIs.put(poiID, poi);
            } else if (boundingBoxPOIs.containsKey(poiID)) {
                viewManager.removePOIFromView(poi);
                boundingBoxPOIs.remove(poiID);
            }
        }

        updateView();
    }

    private void loadPOIs(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude) {
        downloadManager.loadAround(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance, allPOIs);
    }

    private void updateView()
    {
        updateCardinals();

        visible.clear();
        //find elements in view and sort them by distance.

        double fov = Math.max(Globals.observer.fieldOfViewDeg.x / 2.0, Globals.observer.fieldOfViewDeg.y / 2.0);

        for (Long poiID : boundingBoxPOIs.keySet()) {
            GeoNode poi = boundingBoxPOIs.get(poiID);

            double distance = AugmentedRealityUtils.calculateDistance(Globals.observer, poi);
            if (distance < maxDistance) {
                double deltaAzimuth = AugmentedRealityUtils.calculateTheoreticalAzimuth(Globals.observer, poi);
                double difAngle = AugmentedRealityUtils.diffAngle(deltaAzimuth, Globals.observer.degAzimuth);
                if (Math.abs(difAngle) <= fov) {
                    poi.distanceMeters = distance;
                    poi.deltaDegAzimuth = deltaAzimuth;
                    poi.difDegAngle = difAngle;
                    visible.add(poi);
                    continue;
                }
            }
            viewManager.removePOIFromView(poi);
        }

        Collections.sort(visible);

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        int displayLimit = 0;
        zOrderedDisplay.clear();
        for (GeoNode poi: visible)
        {
            if (displayLimit < Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)) {
                displayLimit++;

                zOrderedDisplay.add(poi);
            } else {
                viewManager.removePOIFromView(poi);
            }
        }

        Collections.reverse(zOrderedDisplay);

        for (GeoNode zpoi: zOrderedDisplay) {
            viewManager.addOrUpdatePOIToView(zpoi);
        }
    }

    private void updateCardinals() {
        // Both compass and map location are viewed in the mirror, so they need to be rotated in the opposite direction.
        Quaternion pos = AugmentedRealityUtils.getXYPosition(0, Globals.observer.degPitch,
                Globals.observer.degRoll, Globals.observer.screenRotation,
                new Vector2d(horizon.getLayoutParams().width, horizon.getLayoutParams().height),
                Globals.observer.fieldOfViewDeg, viewManager.rotateDisplaySize);
        horizon.setRotation((float) pos.w);
        horizon.setY((float) pos.y);

        mapWidget.invalidate();
    }

    @Override
    public void onProgress(int progress, boolean hasChanges,  Map<String, Object> parameters) {
        if (progress == 100 && hasChanges) {
            mapWidget.resetPOIs();

            runOnUiThread(new Thread() {
                public void run() {
                    updateBoundingBox(Globals.observer.decimalLatitude, Globals.observer.decimalLongitude, Globals.observer.elevationMeters);
                }
            });
        }
    }
}
