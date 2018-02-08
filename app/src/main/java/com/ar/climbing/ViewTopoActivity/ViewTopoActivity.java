package com.ar.climbing.ViewTopoActivity;

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
import com.ar.climbing.sensors.LocationHandler;
import com.ar.climbing.sensors.SensorListener;
import com.ar.climbing.sensors.camera.AutoFitTextureView;
import com.ar.climbing.sensors.camera.CameraHandler;
import com.ar.climbing.sensors.camera.CameraTextureViewListener;
import com.ar.climbing.utils.AugmentedRealityUtils;
import com.ar.climbing.utils.CompassWidget;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.ILocationListener;
import com.ar.climbing.utils.IOrientationListener;
import com.ar.climbing.utils.MapViewWidget;
import com.ar.climbing.utils.PointOfInterest;
import com.ar.climbing.utils.PointOfInterestDialogBuilder;
import com.ar.climbing.utils.Quaternion;
import com.ar.climbing.utils.Vector2d;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.views.MapView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ViewTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {

    private static final int POS_UPDATE_ANIMATION_STEPS = 10;

    private AutoFitTextureView textureView;
    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;
    private View horizon;

    private Map<Long, PointOfInterest> boundingBoxPOIs = new ConcurrentHashMap<>(); //POIs around the observer.

    private MapViewWidget mapWidget;
    private AugmentedRealityViewManager viewManager;

    private CountDownTimer gpsUpdateAnimationTimer;
    private boolean enableNetFetching = true;

    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
    private double maxDistance;

    private List<PointOfInterest> visible = new ArrayList<>();
    private List<PointOfInterest> zOrderedDisplay = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo);

        //others
        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        this.viewManager = new AugmentedRealityViewManager(this);
        this.mapWidget = new MapViewWidget(this, (MapView)findViewById(R.id.openMapView), Globals.allPOIs);
        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);

        this.horizon = findViewById(R.id.horizon);

        horizon.getLayoutParams().width = (int)Math.sqrt((viewManager.rotateDisplaySize.x * viewManager.rotateDisplaySize.x)
                + (viewManager.rotateDisplaySize.y * viewManager.rotateDisplaySize.y));

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
        maxDistance = Globals.globalConfigs.getMaxDistanceVisibleNodes();
    }

    public void onCompassButtonClick (View v) {
        PointOfInterestDialogBuilder.obsDialogBuilder(v);
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

        if (Globals.globalConfigs.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (!Globals.globalConfigs.getShowVirtualHorizon()) {
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

        downloadPOIs(pDecLatitude, pDecLongitude, pMetersAltitude);

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

        for (Long poiID: Globals.allPOIs.keySet()) {
            PointOfInterest poi = Globals.allPOIs.get(poiID);
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

    private void downloadPOIs(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude) {
        if (!Globals.allowDownload(this.getApplicationContext())) {
            return;
        }

        if (((System.currentTimeMillis() - lastPOINetDownload) < Constants.MINIMUM_CHECK_INTERVAL_MILLISECONDS) && isDownloading.get()) {
            return;
        }

        lastPOINetDownload = System.currentTimeMillis();

        (new Thread() {
            public void run() {
                isDownloading.set(true);
                double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
                double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

                String formData = String.format(Locale.getDefault(),
                        "[out:json][timeout:50];node[\"sport\"=\"climbing\"][~\"^climbing$\"~\"route_bottom\"](%f,%f,%f,%f);out body;",
                        pDecLatitude - deltaLatitude,
                        pDecLongitude - deltaLongitude,
                        pDecLatitude + deltaLatitude,
                        pDecLongitude + deltaLongitude);

                RequestBody body = new FormBody.Builder().add("data", formData).build();
                Request request = new Request.Builder()
                        .url("http://overpass-api.de/api/interpreter")
                        .post(body)
                        .build();
                try (Response response = Constants.httpClient.newCall(request).execute()) {
                    buildPOIsMap(response.body().string());
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                } finally {
                    isDownloading.set(false);
                }
            }
        }).start();
    }

    private void buildPOIsMap(String data) throws JSONException {
        JSONObject jObject = new JSONObject(data);
        JSONArray jArray = jObject.getJSONArray("elements");

        boolean newNode = false;

        for (int i=0; i < jArray.length(); i++) {
            JSONObject nodeInfo = jArray.getJSONObject(i);
            //open street maps ID should be unique since it is a DB ID.
            long nodeID = nodeInfo.getLong("id");
            if (Globals.allPOIs.containsKey(nodeID)) {
                continue;
            }

            PointOfInterest tmpPoi = new PointOfInterest(nodeInfo);
            Globals.allPOIs.put(nodeID, tmpPoi);
            newNode = true;
        }

        if (newNode) {
            mapWidget.resetPOIs();
        }

        runOnUiThread(new Thread() {
            public void run() {
                updateBoundingBox(Globals.observer.decimalLatitude, Globals.observer.decimalLongitude, Globals.observer.elevationMeters);
            }
        });
    }

    private void updateView()
    {
        updateCardinals();

        visible.clear();
        //find elements in view and sort them by distance.

        double fov = Math.max(Globals.observer.fieldOfViewDeg.x / 2.0, Globals.observer.fieldOfViewDeg.y / 2.0);

        for (Long poiID : boundingBoxPOIs.keySet()) {
            PointOfInterest poi = boundingBoxPOIs.get(poiID);

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
        for (PointOfInterest poi: visible)
        {
            if (displayLimit < Globals.globalConfigs.getMaxCountVisibleNodes()) {
                displayLimit++;

                zOrderedDisplay.add(poi);
            } else {
                viewManager.removePOIFromView(poi);
            }
        }

        Collections.reverse(zOrderedDisplay);

        for (PointOfInterest zpoi: zOrderedDisplay) {
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
}