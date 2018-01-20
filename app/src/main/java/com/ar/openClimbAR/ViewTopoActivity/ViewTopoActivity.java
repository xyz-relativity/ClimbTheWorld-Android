package com.ar.openClimbAR.ViewTopoActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.camera.AutoFitTextureView;
import com.ar.openClimbAR.sensors.camera.CameraHandler;
import com.ar.openClimbAR.sensors.camera.CameraTextureViewListener;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.tools.ILocationListener;
import com.ar.openClimbAR.tools.IOrientationListener;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.utils.ArUtils;
import com.ar.openClimbAR.utils.Constants;
import com.ar.openClimbAR.utils.GlobalVariables;
import com.ar.openClimbAR.utils.MapUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;

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
    private MapView osmMap;
    private ArViewManager viewManager;
    private FolderOverlay myMarkersFolder = new FolderOverlay();

    private Marker locationMarker;
    private CountDownTimer gpsUpdateAnimationTimer;
    private boolean enableNetFetching = true;
    private boolean enableMapAutoScroll = true;

    private long lastPOINetDownload = 0;
    private long osmMapClickTimer = 0;

    private String[] cardinalNames;

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

        this.compass = findViewById(R.id.compassView);
        this.viewManager = new ArViewManager(this);
        this.osmMap = findViewById(R.id.openMapView);

        osmMap.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                osmMapClickTimer = System.currentTimeMillis();
                enableMapAutoScroll = false;
                return false;
            }
        });

        //init osm map
        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL);
        osmMap.setMaxZoomLevel(Constants.MAP_MAX_ZOOM_LEVEL);

        GlobalVariables.observer.horizontalFieldOfViewDeg = camera.getDegFOV().getWidth();
        GlobalVariables.observer.screenRotation = ArUtils.getScreenRotationAngle(getWindowManager().getDefaultDisplay().getRotation());

        locationMarker = MapUtils.initMyLocationMarkers(osmMap, myMarkersFolder);

        //location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationHandler = new LocationHandler(locationManager, ViewTopoActivity.this, this, this);

        //orientation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener(this);

        cardinalNames =  getResources().getString(R.string.cardinals_names).split("\\|");
    }

    public void onCompassButtonClick (View v) {

        int azimuthID = (int)Math.floor(Math.abs(GlobalVariables.observer.degAzimuth - 11.25)/22.5);

        AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setTitle(GlobalVariables.observer.name);
        ad.setMessage(v.getResources().getString(R.string.longitude) + ": " + GlobalVariables.observer.decimalLongitude + "°" +
                " " + v.getResources().getString(R.string.latitude) + ": " + GlobalVariables.observer.decimalLatitude + "°" +
                "\n" + v.getResources().getString(R.string.elevation) + ": " + GlobalVariables.observer.elevationMeters + "m" +
                "\n" + v.getResources().getString(R.string.azimuth) + ": " + cardinalNames[azimuthID] + " (" + GlobalVariables.observer.degAzimuth + "°)");
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, v.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }

    // Default onCreateOptionsMenu
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.basic_menu, menu);
        return true;
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

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        GlobalVariables.observer.degAzimuth = pAzimuth;
        GlobalVariables.observer.degPitch = pPitch;
        GlobalVariables.observer.degRoll = pRoll;

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
                    float xStepSize = (pDecLongitude - GlobalVariables.observer.decimalLongitude) / numSteps;
                    float yStepSize = (pDecLatitude - GlobalVariables.observer.decimalLatitude) / numSteps;

                    GlobalVariables.observer.updatePOILocation(GlobalVariables.observer.decimalLatitude + yStepSize,
                            GlobalVariables.observer.decimalLongitude + xStepSize, pMetersAltitude);
                    updateBoundingBox(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude, GlobalVariables.observer.elevationMeters);
                }
            }

            public void onFinish() {
                GlobalVariables.observer.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
                updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
            }
        }.start();
    }

    private void updateBoundingBox(final float pDecLatitude, final float pDecLongitude, final float pMetersAltitude) {
        float deltaLatitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
        float deltaLongitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

        for (Long poiID: GlobalVariables.allPOIs.keySet()) {
            PointOfInterest poi = GlobalVariables.allPOIs.get(poiID);
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
                        "[out:json][timeout:50];node[\"sport\"=\"climbing\"][~\"^climbing:.*$\"~\".\"](%f,%f,%f,%f);out body;",
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
            if (GlobalVariables.allPOIs.containsKey(nodeID)) {
                continue;
            }

            PointOfInterest tmpPoi = new PointOfInterest(nodeInfo);
            GlobalVariables.allPOIs.put(nodeID, tmpPoi);
        }

        runOnUiThread(new Thread() {
            public void run() {
                updateBoundingBox(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude, GlobalVariables.observer.elevationMeters);
            }
        });
    }

    private void updateView()
    {
        updateCardinals();

        List<PointOfInterest> visible = new ArrayList<>();
        //find elements in view and sort them by distance.
        myMarkersFolder = new FolderOverlay();
        myMarkersFolder.add(locationMarker);

        for (Long poiID : boundingBoxPOIs.keySet()) {
            PointOfInterest poi = boundingBoxPOIs.get(poiID);
            MapUtils.addMapMarker(poi, osmMap, myMarkersFolder);

            float distance = ArUtils.calculateDistance(GlobalVariables.observer, poi);
            if (distance < Constants.MAX_DISTANCE_METERS) {
                float deltaAzimuth = ArUtils.calculateTheoreticalAzimuth(GlobalVariables.observer, poi);
                float difAngle = ArUtils.diffAngle(deltaAzimuth, GlobalVariables.observer.degAzimuth);
                if (Math.abs(difAngle) <= (GlobalVariables.observer.horizontalFieldOfViewDeg / 2)) {
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
            viewManager.addOrUpdatePOIToView(zpoi, GlobalVariables.observer);
        }
    }

    private void updateCardinals() {
        // Both compass and map location are viewed in the mirror, so they need to be rotated in the opposite direction.
        compass.setRotation(-GlobalVariables.observer.degAzimuth);
        locationMarker.setRotation(-GlobalVariables.observer.degAzimuth);

        if (enableMapAutoScroll || (System.currentTimeMillis() - osmMapClickTimer) > Constants.MAP_CENTER_FREES_TIMEOUT_MILLISECONDS) {
            osmMap.getController().setCenter(new GeoPoint(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude));
            enableMapAutoScroll = true;
        }

        locationMarker.getPosition().setCoords(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude);
        locationMarker.getPosition().setAltitude(GlobalVariables.observer.elevationMeters);


        osmMap.invalidate();
    }
}
