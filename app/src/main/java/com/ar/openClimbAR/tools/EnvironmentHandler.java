package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.ViewTopoActivity.ArViewManager;
import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.camera.CameraHandler;
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
import org.osmdroid.views.overlay.Overlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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

/**
 * Created by xyz on 11/24/17.
 */

public class EnvironmentHandler implements IEnvironmentHandler {
    private Map<Long, PointOfInterest> boundingBoxPOIs = new ConcurrentHashMap<>(); //POIs around the observer.

    private final Activity activity;
    private final CameraHandler camera;
    private final ImageView compass;
    private final MapView osmMap;
    private final ArViewManager viewManager;
    private final FolderOverlay myMarkersFolder = new FolderOverlay();

    private Marker locationMarker;
    private CountDownTimer gpsUpdateAnimationTimer;
    private boolean enableNetFetching = true;
    private boolean enableMapAutoScroll = true;

    private long lastPOINetDownload = 0;
    private long osmMapClickTimer = 0;

    public EnvironmentHandler(Activity pActivity, CameraHandler pCamera)
    {
        this.activity = pActivity;
        this.camera = pCamera;

        this.compass = activity.findViewById(R.id.compassView);
        this.viewManager = new ArViewManager(activity);
        this.osmMap = activity.findViewById(R.id.openMapView);

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
        GlobalVariables.observer.screenRotation = ArUtils.getScreenRotationAngle(activity.getWindowManager().getDefaultDisplay().getRotation());

        locationMarker = MapUtils.initMyLocationMarkers(osmMap, myMarkersFolder);
        for (long poiID : GlobalVariables.allPOIs.keySet()) {
            MapUtils.addMapMarker(GlobalVariables.allPOIs.get(poiID), osmMap, myMarkersFolder);
        }

//        enableNetFetching = !initPOIFromDB();
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

            MapUtils.addMapMarker(tmpPoi, osmMap, myMarkersFolder);
        }

        activity.runOnUiThread(new Thread() {
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
        for (Long poiID : boundingBoxPOIs.keySet()) {
            PointOfInterest poi = boundingBoxPOIs.get(poiID);
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
        compass.setRotation(-GlobalVariables.observer.degAzimuth);
        compass.setRotationX(-GlobalVariables.observer.degPitch);
        compass.setRotationY(GlobalVariables.observer.degRoll + GlobalVariables.observer.screenRotation);
        compass.requestLayout();

        if (enableMapAutoScroll || (System.currentTimeMillis() - osmMapClickTimer) > Constants.MAP_CENTER_FREES_TIMEOUT_MILLISECONDS) {
            osmMap.getController().setCenter(new GeoPoint(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude));
            enableMapAutoScroll = true;
        }

        locationMarker.getPosition().setCoords(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude);
        locationMarker.getPosition().setAltitude(GlobalVariables.observer.elevationMeters);

        locationMarker.setRotation(GlobalVariables.observer.degAzimuth);
        osmMap.invalidate();
    }

}
