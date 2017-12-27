package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.CountDownTimer;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.camera.CameraHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.ar.openClimbAR.tools.PointOfInterest.POIType.climbing;

/**
 * Created by xyz on 11/24/17.
 */

public class EnvironmentHandler {
    private static final float MAX_DISTANCE_METERS = 500000f;
    private static final float MIN_DISTANCE_METERS = 0f;
    private static final float UI_MIN_SCALE = 20f;
    private static final float UI_MAX_SCALE = 300f;
    private static final int MAX_SHOW_NODES = 100;
    private static final int MAP_ZOOM_LEVEL = 16;

    private OrientationPointOfInterest observer = new OrientationPointOfInterest(PointOfInterest.POIType.observer,
            0f, 0f,
            100f);

    private Map<Long, PointOfInterest> allPOIs = new ConcurrentHashMap<>(); //database
    private Map<Long, PointOfInterest> boundingBoxPOIs = new ConcurrentHashMap<>(); //POIs around the observer.
    private Map<PointOfInterest, View> toDisplay = new HashMap<>(); //Visible POIs

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Activity parentActivity;
    private final CameraHandler camera;
    private final ImageView compass;
    private final MapView osmMap;
    private final RelativeLayout buttonContainer;

    private CountDownTimer animTimer;
    private boolean enableNetFetching = true;

    public EnvironmentHandler(Activity pActivity, CameraHandler pCamera)
    {
        this.parentActivity = pActivity;
        this.camera = pCamera;

        this.compass = parentActivity.findViewById(R.id.compassView);

        buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        osmMap = parentActivity.findViewById(R.id.openMapView);

        //init osm map
        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.OpenTopo);
        osmMap.getController().setZoom(MAP_ZOOM_LEVEL);

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
        InputStream is = parentActivity.getResources().openRawResource(R.raw.world_db);

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
        float deltaLatitude = (float)Math.toDegrees(MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
        float deltaLongitude = (float)Math.toDegrees(MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

        for (Long poiID: allPOIs.keySet()) {
            PointOfInterest poi = allPOIs.get(poiID);
            if ((poi.decimalLatitude > pDecLatitude - deltaLatitude && poi.decimalLatitude < pDecLatitude + deltaLatitude)
                    && (poi.decimalLongitude > pDecLongitude - deltaLongitude && poi.decimalLongitude < pDecLongitude + deltaLongitude)) {

                boundingBoxPOIs.put(poiID, poi);
            } else if (boundingBoxPOIs.containsKey(poiID)) {

                if (toDisplay.containsKey(poi)){
                    deleteViewElement(toDisplay.get(poi));
                    toDisplay.remove(poi);
                }
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

                float deltaLatitude = (float)Math.toDegrees(MAX_DISTANCE_METERS / ArUtils.EARTH_RADIUS_M);
                float deltaLongitude = (float)Math.toDegrees(MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * ArUtils.EARTH_RADIUS_M));

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
                try (Response response = httpClient.newCall(request).execute()) {
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
        observer.screenRotation = ArUtils.getScreenRotationAngle(parentActivity);

        updateCardinals();

        TreeSet<PointOfInterest> visible = new TreeSet<>();
        //find elements in view and sort them by distance.
        for (Long poiID : boundingBoxPOIs.keySet()) {
            PointOfInterest poi = boundingBoxPOIs.get(poiID);
            float distance = ArUtils.calculateDistance(observer, poi);
            if (distance < MAX_DISTANCE_METERS) {
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
            if (toDisplay.containsKey(poi)) {
                deleteViewElement(toDisplay.get(poi));
                toDisplay.remove(poi);
            }
        }

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        int displayLimit = 0;
        for (PointOfInterest ui: visible)
        {
            if (displayLimit < MAX_SHOW_NODES) {
                displayLimit++;

                if (!toDisplay.containsKey(ui)) {
                    toDisplay.put(ui, addViewElementFromTemplate(ui));
                }
                updateViewElement(toDisplay.get(ui), ui);
            } else {
                if (toDisplay.containsKey(ui)) {
                    deleteViewElement(toDisplay.get(ui));
                    toDisplay.remove(ui);
                }
            }
        }

        System.out.println("db: " + allPOIs.size() + " bbox: " + boundingBoxPOIs.size() + " visible: " + visible.size() + " toDisplay:" + toDisplay.size());
    }

    private void updateCardinals() {
        compass.setRotation(-observer.degAzimuth);
        compass.setRotationX(-observer.degPitch);
        compass.setRotationY(observer.degRoll + observer.screenRotation);
        compass.requestLayout();

        osmMap.getController().setCenter(new GeoPoint(observer.decimalLatitude, observer.decimalLongitude));
        osmMap.setMapOrientation(-observer.degAzimuth);
    }

    private View addViewElementFromTemplate(PointOfInterest poi) {
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.topo_display_button, null);
        newViewElement.setOnClickListener(new TopoButtonClickListener(parentActivity, poi));

        float remapGradeScale = ArUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.getLevel());
        ((ImageButton)newViewElement).setImageTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));

        buttonContainer.addView(newViewElement);

        return newViewElement;
    }

    private void addMapMarker(PointOfInterest poi) {
        ArrayList<OverlayItem> items = new ArrayList<>();
        items.add(new OverlayItem(poi.name, String.valueOf(poi.getLevel()), new GeoPoint(poi.decimalLatitude, poi.decimalLongitude)));

        ItemizedOverlayWithFocus<OverlayItem> mOverlay = new ItemizedOverlayWithFocus<>(items,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override
                    public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        return true;
                    }
                    @Override
                    public boolean onItemLongPress(final int index, final OverlayItem item) {
                        return false;
                    }
                }, parentActivity);
        mOverlay.setFocusItemsOnTap(false);

        osmMap.getOverlays().add(mOverlay);
    }

    private void deleteViewElement(View button) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        buttonContainer.removeView(button);
    }

    private void updateViewElement(View pButton, PointOfInterest ui) {
        int size = calculateSizeInDPI(ui.distance);
        int sizeX = (int)(size*0.5);
        int sizeY = size;

        float[] pos = ArUtils.getXYPosition(ui.difDegAngle, observer.degPitch, observer.degRoll, observer.screenRotation, sizeX, sizeY, observer.horizontalFieldOfViewDeg);
        float xPos = pos[0];
        float yPos = pos[1];
        float roll = pos[2];

        pButton.getLayoutParams().height = sizeY;
        pButton.getLayoutParams().width = sizeX;

        pButton.setX(xPos);
        pButton.setY(yPos);
        pButton.setRotation(roll);

        pButton.setRotationX(observer.degPitch);

        pButton.bringToFront();
        pButton.requestLayout();
    }

    private int calculateSizeInDPI(float distance) {
        int result = Math.round(ArUtils.remapScale(MIN_DISTANCE_METERS, MAX_DISTANCE_METERS, UI_MIN_SCALE, UI_MAX_SCALE, distance));

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                result, parentActivity.getResources().getDisplayMetrics());
    }
}
