package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.CountDownTimer;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Surface;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
    private static final float MAX_DISTANCE_METERS = 50f;
    private static final float MIN_DISTANCE_METERS = 0f;
    private static final float UI_MIN_SCALE = 20f;
    private static final float UI_MAX_SCALE = 300f;
    private static final double EARTH_RADIUS_KM = 6371f;
    private static final double EARTH_RADIUS_M = EARTH_RADIUS_KM * 1000f;
    private static final int MAX_SHOW_NODES = 30;
    private static final int MAP_ZOOM_LEVEL = 16;

    private float degAzimuth = 0;
    private float degPitch = 0;
    private float degRoll = 0;
    private float horizontalFieldOfViewDeg = 0;
    private PointOfInterest observer = new PointOfInterest(PointOfInterest.POIType.observer,
            0f, 0f,
            100f);

    private Map<Long, PointOfInterest> pois = new ConcurrentHashMap<>();
    private Map<PointOfInterest, View> toDisplay = new HashMap<>();

    private final Activity parentActivity;
    private final CameraHandler camera;
    private final ImageView compass;
    private final MapView osmMap;
    private CountDownTimer animTimer;
    private RelativeLayout buttonContainer;

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
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        this.degAzimuth = pAzimuth;
        this.degPitch = pPitch;
        this.degRoll = pRoll;

        updateView();
    }

    public void updatePosition(final float pDecLongitude, final float pDecLatitude, final float pMetersAltitude, final float accuracy) {
        final int animationInterval = 100;

        updatePOIs(pDecLongitude, pDecLatitude, pMetersAltitude);

        if (animTimer != null)
        {
            animTimer.cancel();
        }

        animTimer = new CountDownTimer(LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL, animationInterval) {
            public void onTick(long millisUntilFinished) {
                long numSteps = (millisUntilFinished) / animationInterval;
                if (numSteps != 0) {
                    float xStepSize = (pDecLongitude - observer.getDecimalLongitude()) / numSteps;
                    float yStepSize = (pDecLatitude - observer.getDecimalLatitude()) / numSteps;

                    observer.updatePOILocation(observer.getDecimalLongitude() + xStepSize,
                            observer.getDecimalLatitude() + yStepSize, pMetersAltitude);
                    updateView();
                }
            }

            public void onFinish() {
                observer.updatePOILocation(pDecLongitude, pDecLatitude, pMetersAltitude);
                updateView();
            }
        }.start();
    }

    private void updatePOIs(final float pDecLongitude, final float pDecLatitude, final float pMetersAltitude) {
        (new Thread() {
            public void run() {

                float deltaLatitude = (float)Math.toDegrees(MAX_DISTANCE_METERS / EARTH_RADIUS_M);
                float deltaLongitude = (float)Math.toDegrees(MAX_DISTANCE_METERS / (Math.cos(Math.toRadians(pDecLatitude)) * EARTH_RADIUS_M));

                String formData = String.format(Locale.getDefault(),"[out:json][timeout:50];node[\"sport\"=\"climbing\"][~\"^climbing:.*$\"~\".\"](%f,%f,%f,%f);out body;",
                        pDecLatitude - deltaLatitude,
                        pDecLongitude - deltaLongitude,
                        pDecLatitude + deltaLatitude,
                        pDecLongitude + deltaLongitude);

                OkHttpClient client = new OkHttpClient();
                RequestBody body = new FormBody.Builder().add("data", formData).build();
                Request request = new Request.Builder()
                        .url("http://overpass-api.de/api/interpreter")
                        .post(body)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    JSONObject jObject = new JSONObject(response.body().string());
                    JSONArray jArray = jObject.getJSONArray("elements");

                    for (int i=0; i < jArray.length(); i++)
                    {
                        JSONObject nodeInfo = jArray.getJSONObject(i);
                        //open street maps ID should be unique since it is a DB ID.
                        if (pois.containsKey(nodeInfo.getLong("id"))) {
                            continue;
                        }

                        JSONObject nodeTags = nodeInfo.getJSONObject("tags");

                        PointOfInterest tmpPoi = new PointOfInterest(climbing,
                                Float.parseFloat(nodeInfo.getString("lon")),
                                Float.parseFloat(nodeInfo.getString("lat")),
                                Float.parseFloat(nodeTags.optString("ele", "0").replaceAll("[^\\d.]", "")));

                        tmpPoi.updatePOIInfo(nodeTags.optString("name", "MISSING"), nodeTags);
                        pois.put(nodeInfo.getLong("id"), tmpPoi);

                        addMapMarker(tmpPoi);
                    }

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateView()
    {
        horizontalFieldOfViewDeg = camera.getDegFOV().getWidth();

        updateCardinals();

        TreeSet<DisplayPOI> visible = new TreeSet<>();
        //find elements in view and sort them by distance.
        for (Long poiID: pois.keySet())
        {
            PointOfInterest poi = pois.get(poiID);
            float distance = calculateDistance(observer, poi);
            if (distance < MAX_DISTANCE_METERS) {
                float deltaAzimuth = calculateTheoreticalAzimuth(observer, poi);
                float difAngle = diffAngle(deltaAzimuth, degAzimuth);
                if (Math.abs(difAngle) <= (horizontalFieldOfViewDeg /2)) {
                    visible.add(new DisplayPOI(distance, deltaAzimuth, difAngle, poi));
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
        for (DisplayPOI ui: visible)
        {
            if (displayLimit < MAX_SHOW_NODES) {
                displayLimit++;

                if (!toDisplay.containsKey(ui.poi)) {
                    toDisplay.put(ui.poi, addViewElementFromTemplate(ui));
                }
                updateViewElement(toDisplay.get(ui.poi), ui);
            } else {
                if (toDisplay.containsKey(ui.poi)) {
                    deleteViewElement(toDisplay.get(ui.poi));
                    toDisplay.remove(ui.poi);
                }
            }
        }
    }

    private void updateCardinals() {
        compass.setRotation(-1 * degAzimuth);
        compass.setRotationX(-1 * degPitch);
        compass.setRotationY(degRoll + getScreenRotationAngle());
        compass.requestLayout();

        osmMap.getController().setCenter(new GeoPoint(observer.getDecimalLatitude(), observer.getDecimalLongitude()));
    }

    private float[] getXYPosition(float yawDegAngle, float pitch, float pRoll, float sizeX, float sizeY) {
        float screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        float screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        float roll = pRoll + getScreenRotationAngle();

        float absoluteY = (((pitch * screenHeight) / (horizontalFieldOfViewDeg)) + (screenHeight/2)) - (sizeY/2);
        float radius = ((yawDegAngle * screenWidth) / (horizontalFieldOfViewDeg)) - (sizeX/2);

        float[] result = new float[3];

        result[0] = (float)(radius * Math.cos(Math.toRadians(roll))) + (screenWidth/2);
        result[1] = (float)(radius * Math.sin(Math.toRadians(roll))) + absoluteY;
        result[2] = roll;

        return result;
    }

    private View addViewElementFromTemplate(DisplayPOI poi) {
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.topo_display_button, null);
        newViewElement.setOnClickListener(new TopoButtonClickListener(parentActivity, poi));

        float remapGradeScale = remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.poi.getLevel());
        ((ImageButton)newViewElement).setImageTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));

        buttonContainer.addView(newViewElement);

        return newViewElement;
    }

    private void addMapMarker(PointOfInterest poi) {
        ArrayList<OverlayItem> items = new ArrayList<>();
        items.add(new OverlayItem(poi.getName(), String.valueOf(poi.getLevel()), new GeoPoint(poi.getDecimalLatitude(), poi.getDecimalLongitude())));

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

    private void updateViewElement(View pButton, DisplayPOI ui) {
        int size = calculateSizeInDPI(ui.distance);
        int sizeX = (int)(size*0.5);
        int sizeY = size;

        float[] pos = getXYPosition(ui.difDegAngle, degPitch, degRoll, sizeX, sizeY);
        float xPos = pos[0];
        float yPos = pos[1];
        float roll = pos[2];

        pButton.getLayoutParams().height = sizeY;
        pButton.getLayoutParams().width = sizeX;

        pButton.setX(xPos);
        pButton.setY(yPos);
        pButton.setRotation(roll);

        pButton.setRotationX(degPitch);

        pButton.bringToFront();
        pButton.requestLayout();
    }

    private float getScreenRotationAngle() {
        int rotation =  parentActivity.getWindowManager().getDefaultDisplay().getRotation();

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

    private float remapScale(float orgMin, float orgMax, float newMin, float newMax, float pos) {
        float oldRange = (orgMax - orgMin);
        float result;
        if (oldRange == 0)
            result = newMax;
        else
        {
            result = (((pos - orgMin) * (newMin - newMax)) / oldRange) + newMax;
        }

        return result;
    }

    private int calculateSizeInDPI(float distance) {
        int result = Math.round(remapScale(MIN_DISTANCE_METERS, MAX_DISTANCE_METERS, UI_MIN_SCALE, UI_MAX_SCALE, distance));

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                result, parentActivity.getResources().getDisplayMetrics());
    }

    private float calculateTheoreticalAzimuth(PointOfInterest obs, PointOfInterest poi) {
        return (float)Math.toDegrees(Math.atan2(poi.getDecimalLongitude() - obs.getDecimalLongitude(),
                poi.getDecimalLatitude() - obs.getDecimalLatitude()));
    }

    /**
     * Calculate distance between 2 coordinates using the haversine algorithm.
     * @param obs Observer location
     * @param poi Point of interest location
     * @return Shortest as the crow flies distance in meters.
     */
    private float calculateDistance(PointOfInterest obs, PointOfInterest poi) {
        double dLat = Math.toRadians(poi.getDecimalLatitude()-obs.getDecimalLatitude());
        double dLon = Math.toRadians(poi.getDecimalLongitude()-obs.getDecimalLongitude());

        double lat1 = Math.toRadians(obs.getDecimalLatitude());
        double lat2 = Math.toRadians(poi.getDecimalLatitude());

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return (float)((EARTH_RADIUS_KM * c)*1000f);
    }

    private float diffAngle(float a, float b) {
//        double x = Math.toRadians(a);
//        double y = Math.toRadians(b);
//        return (float)Math.toDegrees(Math.atan2(Math.sin(x-y), Math.cos(x-y)));

        //this way should be more efficient
        float d = Math.abs(a - b) % 360;
        float r = d > 180 ? 360 - d : d;

        int sign = (a - b >= 0 && a - b <= 180) || (a - b <=-180 && a- b>= -360) ? 1 : -1;
        return (r * sign);
    }

    //debug code
    private void initPOIS(int count) {
        float minLat = -0.005f;
        float maxLat = 0.005f;

        float minLong = -0.005f;
        float maxLong = 0.005f;

        float minAlt = -1f;
        float maxAlt = 1f;

        Random rand = new Random();

        for (Long i = 0l; i < count; ++i) {
            float finalLong = rand.nextFloat() * (maxLong - minLong) + minLong;
            float finalLat = rand.nextFloat() * (maxLat - minLat) + minLat;
            float finalAlt = rand.nextFloat() * (maxAlt - minAlt) + minAlt;

            PointOfInterest tmpPoi = new PointOfInterest(climbing,
                    observer.getDecimalLongitude() + finalLong,
                    observer.getDecimalLatitude() + finalLat,
                    observer.getAltitudeMeters() + finalAlt);
            try {
                tmpPoi.updatePOIInfo("test" + i, new JSONObject("{\"climbing:grade:saxon:min\": \"I\",\n" +
                        "    \"name\": \"Bahnhofswächter\",\n" +
                        "    \"natural\": \"peak\",\n" +
                        "    \"sport\": \"climbing\"}"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            pois.put(i, tmpPoi);
        }
    }
}
