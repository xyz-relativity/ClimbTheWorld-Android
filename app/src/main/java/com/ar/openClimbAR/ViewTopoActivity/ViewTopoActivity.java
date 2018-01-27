package com.ar.openClimbAR.ViewTopoActivity;

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
import android.widget.ImageView;
import android.widget.Toast;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.sensors.camera.AutoFitTextureView;
import com.ar.openClimbAR.sensors.camera.CameraHandler;
import com.ar.openClimbAR.sensors.camera.CameraTextureViewListener;
import com.ar.openClimbAR.tools.ILocationListener;
import com.ar.openClimbAR.tools.IOrientationListener;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterestDialogBuilder;
import com.ar.openClimbAR.utils.ArUtils;
import com.ar.openClimbAR.utils.Constants;
import com.ar.openClimbAR.utils.Globals;
import com.ar.openClimbAR.utils.MapViewWidget;

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

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ViewTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {

    private AutoFitTextureView textureView;
    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;

    private Map<Long, PointOfInterest> boundingBoxPOIs = new ConcurrentHashMap<>(); //POIs around the observer.

    private ImageView compass;
    private MapViewWidget mapWidget;
    private ArViewManager viewManager;

    private CountDownTimer gpsUpdateAnimationTimer;
    private boolean enableNetFetching = true;

    private long lastPOINetDownload = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo);

        //camera
        textureView = findViewById(R.id.texture);
        assert textureView != null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
                    ViewTopoActivity.this, this, textureView);
            cameraTextureListener = new CameraTextureViewListener(camera);
            textureView.setSurfaceTextureListener(cameraTextureListener);
        }

        this.compass = findViewById(R.id.compassButton);
        this.viewManager = new ArViewManager(this);
        this.mapWidget = new MapViewWidget(this, (MapView)findViewById(R.id.openMapView), Globals.allPOIs);

        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);

        Globals.observer.horizontalFieldOfViewDeg = camera.getDegFOV().getWidth();
        Globals.observer.screenRotation = ArUtils.getScreenRotationAngle(getWindowManager().getDefaultDisplay().getRotation());

        //location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationHandler = new LocationHandler(locationManager, ViewTopoActivity.this, this);
        locationHandler.addListener(this);

        //orientation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this);
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
                SensorManager.SENSOR_DELAY_NORMAL);

        if (Constants.KEEP_SCREEN_ON) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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

        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
            mapWidget.resetPOIs();
            mapWidget.invalidate();
        }
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        Globals.observer.degAzimuth = pAzimuth;
        Globals.observer.degPitch = pPitch;
        Globals.observer.degRoll = pRoll;

        updateView();
    }

    public void updatePosition(final float pDecLatitude, final float pDecLongitude, final float pMetersAltitude, final float accuracy) {
        final int animationInterval = 100;

        downloadPOIs(pDecLatitude, pDecLongitude, pMetersAltitude);

        if (gpsUpdateAnimationTimer != null)
        {
            gpsUpdateAnimationTimer.cancel();
        }

        //Do a nice animation when moving to a new GPS position.
        gpsUpdateAnimationTimer = new CountDownTimer(LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL, animationInterval) {
            public void onTick(long millisUntilFinished) {
                long numSteps = (millisUntilFinished) / animationInterval;
                if (numSteps != 0) {
                    float xStepSize = (pDecLongitude - Globals.observer.decimalLongitude) / numSteps;
                    float yStepSize = (pDecLatitude - Globals.observer.decimalLatitude) / numSteps;

                    Globals.observer.updatePOILocation(Globals.observer.decimalLatitude + yStepSize,
                            Globals.observer.decimalLongitude + xStepSize, pMetersAltitude);
                    updateBoundingBox(Globals.observer.decimalLatitude, Globals.observer.decimalLongitude, Globals.observer.elevationMeters);
                }
            }

            public void onFinish() {
                Globals.observer.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
                Globals.observer.horizontalFieldOfViewDeg = camera.getDegFOV().getWidth();
                updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
            }
        }.start();
    }

    private void updateBoundingBox(final float pDecLatitude, final float pDecLongitude, final float pMetersAltitude) {
        float deltaLatitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
        float deltaLongitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

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

    private void downloadPOIs(final float pDecLatitude, final float pDecLongitude, final float pMetersAltitude) {
        if (!enableNetFetching) {
            return;
        }

        if ((System.currentTimeMillis() - lastPOINetDownload) < Constants.MINIMUM_CHECK_INTERVAL_MILLISECONDS) {
            return;
        }

        lastPOINetDownload = System.currentTimeMillis();

        (new Thread() {
            public void run() {

                float deltaLatitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
                float deltaLongitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

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
                }
            }
        }).start();
    }

    private void buildPOIsMap(String data) throws JSONException {
        JSONObject jObject = new JSONObject(data);
        JSONArray jArray = jObject.getJSONArray("elements");

        for (int i=0; i < jArray.length(); i++) {
            JSONObject nodeInfo = jArray.getJSONObject(i);
            //open street maps ID should be unique since it is a DB ID.
            long nodeID = nodeInfo.getLong("id");
            if (Globals.allPOIs.containsKey(nodeID)) {
                continue;
            }

            PointOfInterest tmpPoi = new PointOfInterest(nodeInfo);
            Globals.allPOIs.put(nodeID, tmpPoi);
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

        List<PointOfInterest> visible = new ArrayList<>();
        //find elements in view and sort them by distance.

        for (Long poiID : boundingBoxPOIs.keySet()) {
            PointOfInterest poi = boundingBoxPOIs.get(poiID);

            float distance = ArUtils.calculateDistance(Globals.observer, poi);
            if (distance < Constants.MAX_DISTANCE_METERS) {
                float deltaAzimuth = ArUtils.calculateTheoreticalAzimuth(Globals.observer, poi);
                float difAngle = ArUtils.diffAngle(deltaAzimuth, Globals.observer.degAzimuth);
                if (Math.abs(difAngle) <= (Globals.observer.horizontalFieldOfViewDeg / 2)) {
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
        List<PointOfInterest> zOrderedDisplay = new ArrayList<>();
        for (PointOfInterest poi: visible)
        {
            if (displayLimit < Constants.MAX_SHOW_NODES) {
                displayLimit++;

                zOrderedDisplay.add(poi);
            } else {
                viewManager.removePOIFromView(poi);
            }
        }

        Collections.reverse(zOrderedDisplay);

        for (PointOfInterest zpoi: zOrderedDisplay) {
            viewManager.addOrUpdatePOIToView(zpoi, Globals.observer);
        }
    }

    private void updateCardinals() {
        // Both compass and map location are viewed in the mirror, so they need to be rotated in the opposite direction.
        compass.setRotation(Globals.observer.degAzimuth);

        mapWidget.invalidate();
    }
}
