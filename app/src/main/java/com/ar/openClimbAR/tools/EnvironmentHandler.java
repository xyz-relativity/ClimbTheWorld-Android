package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.view.Surface;
import android.widget.ImageView;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.ViewTopoActivity.ArViewManager;
import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.camera.CameraHandler;
import com.ar.openClimbAR.utils.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.ar.openClimbAR.tools.PointOfInterest.POIType.climbing;

/**
 * Created by xyz on 11/24/17.
 */

public class EnvironmentHandler {
    private OrientationPointOfInterest observer = new OrientationPointOfInterest(PointOfInterest.POIType.observer,
            0f, 0f,
            100f);

    private Map<Long, PointOfInterest> allPOIs = new ConcurrentHashMap<>(); //database
    private Map<Long, PointOfInterest> boundingBoxPOIs = new ConcurrentHashMap<>(); //POIs around the observer.

    private final Activity activity;
    private final CameraHandler camera;
    private final ImageView compass;
    private final MapView osmMap;
    private final ArViewManager viewManager;

    private CountDownTimer animTimer;
    private boolean enableNetFetching = true;

    public EnvironmentHandler(Activity pActivity, CameraHandler pCamera)
    {
        this.activity = pActivity;
        this.camera = pCamera;

        this.compass = activity.findViewById(R.id.compassView);

        viewManager = new ArViewManager(activity);

        osmMap = activity.findViewById(R.id.openMapView);

        //init osm map
        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.OpenTopo);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL);

        MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(osmMap);
        osmMap.getOverlays().add(myLocationOverlay);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);

        enableNetFetching = !initPOIFromDB();
    }

    public OrientationPointOfInterest getObserver() {
        return observer;
    }

    private boolean initPOIFromDB() {
        InputStream is = activity.getResources().openRawResource(R.raw.world_db);

        if (is == null) {
            return false;
        }

        BufferedReader reader = null;
        reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        String line = "";
        try {
            StringBuilder responseStrBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                responseStrBuilder.append(line);
            }

            buildPOIsMap(responseStrBuilder.toString());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        observer.degAzimuth = pAzimuth;
        observer.degPitch = pPitch;
        observer.degRoll = pRoll;

        updateView();
    }

    public void updatePosition(final float pDecLongitude, final float pDecLatitude, final float pMetersAltitude, final float accuracy) {
        final int animationInterval = 100;

        downloadPOIs(pDecLongitude, pDecLatitude, pMetersAltitude);

        if (animTimer != null)
        {
            animTimer.cancel();
        }

        animTimer = new CountDownTimer(LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL, animationInterval) {
            public void onTick(long millisUntilFinished) {
                long numSteps = (millisUntilFinished) / animationInterval;
                if (numSteps != 0) {
                    float xStepSize = (pDecLongitude - observer.decimalLongitude) / numSteps;
                    float yStepSize = (pDecLatitude - observer.decimalLatitude) / numSteps;

                    observer.updatePOILocation(observer.decimalLongitude + xStepSize,
                            observer.decimalLatitude + yStepSize, pMetersAltitude);
                    updateBoundingBox(observer.decimalLongitude, observer.decimalLatitude, observer.altitudeMeters);
                }
            }

            public void onFinish() {
                observer.updatePOILocation(pDecLongitude, pDecLatitude, pMetersAltitude);
                updateBoundingBox(pDecLongitude, pDecLatitude, pMetersAltitude);
            }
        }.start();
    }

    private void updateBoundingBox(final float pDecLongitude, final float pDecLatitude, final float pMetersAltitude) {
        float deltaLatitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
        float deltaLongitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

        for (Long poiID: allPOIs.keySet()) {
            PointOfInterest poi = allPOIs.get(poiID);
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

    private void downloadPOIs(final float pDecLongitude, final float pDecLatitude, final float pMetersAltitude) {
        if (!enableNetFetching) {
            return;
        }

        (new Thread() {
            public void run() {

                float deltaLatitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
                float deltaLongitude = (float)Math.toDegrees(Constants.MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

                String formData = String.format(Locale.getDefault(),"[out:json][timeout:50];node[\"sport\"=\"climbing\"][~\"^climbing:.*$\"~\".\"](%f,%f,%f,%f);out body;",
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
            if (allPOIs.containsKey(nodeID)) {
                continue;
            }

            JSONObject nodeTags = nodeInfo.getJSONObject("tags");

            PointOfInterest tmpPoi = new PointOfInterest(climbing,
                    Float.parseFloat(nodeInfo.getString("lon")),
                    Float.parseFloat(nodeInfo.getString("lat")),
                    Float.parseFloat(nodeTags.optString("ele", "0").replaceAll("[^\\d.]", "")));

            tmpPoi.updatePOIInfo(nodeTags.optString("name", "MISSING NAME"), nodeTags);
            allPOIs.put(nodeID, tmpPoi);

            addMapMarker(tmpPoi);
        }
    }

    private void updateView()
    {
        observer.horizontalFieldOfViewDeg = camera.getDegFOV().getWidth();
        observer.screenRotation = getScreenRotationAngle();

        updateCardinals();

        TreeSet<PointOfInterest> visible = new TreeSet<>();
        //find elements in view and sort them by distance.
        for (Long poiID : boundingBoxPOIs.keySet()) {
            PointOfInterest poi = boundingBoxPOIs.get(poiID);
            float distance = ArUtils.calculateDistance(observer, poi);
            if (distance < Constants.MAX_DISTANCE_METERS) {
                float deltaAzimuth = ArUtils.calculateTheoreticalAzimuth(observer, poi);
                float difAngle = ArUtils.diffAngle(deltaAzimuth, observer.degAzimuth);
                if (Math.abs(difAngle) <= (observer.horizontalFieldOfViewDeg / 2)) {
                    poi.distance = distance;
                    poi.deltaDegAzimuth = deltaAzimuth;
                    poi.difDegAngle = difAngle;
                    visible.add(poi);
                    continue;
                }
            }
            viewManager.removePOIFromView(poi);
        }

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        int displayLimit = 0;
        for (PointOfInterest poi: visible)
        {
            if (displayLimit < Constants.MAX_SHOW_NODES) {
                displayLimit++;

                viewManager.addOrUpdatePOIToView(poi, observer);
            } else {
                viewManager.removePOIFromView(poi);
            }
        }
    }

    private void updateCardinals() {
        compass.setRotation(-observer.degAzimuth);
        compass.setRotationX(-observer.degPitch);
        compass.setRotationY(observer.degRoll + observer.screenRotation);
        compass.requestLayout();

        osmMap.getController().setCenter(new GeoPoint(observer.decimalLatitude, observer.decimalLongitude));
        osmMap.setMapOrientation(-observer.degAzimuth);
    }

    private void addMapMarker(final PointOfInterest poi) {
//        FolderOverlay myMarkersFolder = new FolderOverlay();
//        List<Overlay> list = myMarkersFolder.getItems();
//
//        Drawable nodeIcon = activity.getResources().getDrawable(R.drawable.marker_default);
//
//        float remapGradeScale = ArUtils.remapScale(0f,
//                GradeConverter.getConverter().maxGrades,
//                0f,
//                1f,
//                poi.getLevel());
//        nodeIcon.setTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));
//        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
//
//        Marker nodeMarker = new Marker(osmMap);
//        nodeMarker.setPosition(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));
//        nodeMarker.setIcon(nodeIcon);
//        nodeMarker.setTitle(poi.name);
//        nodeMarker.setSubDescription(GradeConverter.getConverter().getGradeFromOrder("UIAA", poi.getLevel()) +" (UIAA)");
//        nodeMarker.setImage(nodeIcon);
//        nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
//            @Override
//            public boolean onMarkerClick(Marker marker, MapView mapView) {
//                marker.showInfoWindow();
//                return true;
//            }
//        });
//
//        //put into FolderOverlay list
//        list.add(nodeMarker);
//
//        osmMap.getOverlays().add(myMarkersFolder);
//        osmMap.invalidate();


        ArrayList<OverlayItem> items = new ArrayList<>();
        OverlayItem item = new OverlayItem(String.valueOf(poi.getLevel()), poi.name, new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));
        Drawable nodeIcon = activity.getResources().getDrawable(R.drawable.marker_default);
        float remapGradeScale = ArUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.getLevel());
        nodeIcon.setTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
        item.setMarker(nodeIcon);

        items.add(item);

        ItemizedOverlayWithFocus<OverlayItem> mOverlay = new ItemizedOverlayWithFocus<>(items,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override
                    public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        return false;
                    }
                    @Override
                    public boolean onItemLongPress(final int index, final OverlayItem item) {
                        return false;
                    }
                }, activity);
        mOverlay.setFocusItemsOnTap(false);

        osmMap.getOverlays().add(mOverlay);
        osmMap.invalidate();
    }


    public float getScreenRotationAngle() {
        int rotation =  activity.getWindowManager().getDefaultDisplay().getRotation();

        float angle = 0;
        switch (rotation) {
            case Surface.ROTATION_90:
                angle = -90;
                break;
            case Surface.ROTATION_180:
                angle = 180;
                break;
            case Surface.ROTATION_270:
                angle = 90;
                break;
            default:
                angle = 0;
                break;
        }
        return angle;
    }

}
