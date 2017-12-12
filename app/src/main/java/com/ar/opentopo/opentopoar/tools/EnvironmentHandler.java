package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.CountDownTimer;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.ar.opentopo.opentopoar.R;
import com.ar.opentopo.opentopoar.sensors.LocationHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import static com.ar.opentopo.opentopoar.tools.PointOfInterest.POIType.climbing;

/**
 * Created by xyz on 11/24/17.
 */

public class EnvironmentHandler {
    private static final float VIEW_ANGLE_DEG = 60f;
    private static final float MAX_DISTANCE_METERS = 100f;
    private static final float MIN_DISTANCE_METERS = 0f;
    private static final float UI_MIN_SCALE = 20f;
    private static final float UI_MAX_SCALE = 300f;
    private static final double EARTH_RADIUS_KM = 6371;

    private float degAzimuth = 0;
    private float degPitch = 0;
    private float degRoll = 0;
    private PointOfInterest observer = new PointOfInterest(PointOfInterest.POIType.observer,
            -74.33246f, 45.46704f,
            100f);

    private List<PointOfInterest> pois = new ArrayList<>();
    private Map<PointOfInterest, View> toDisplay = new HashMap<>();

    private final Activity parentActivity;
    private final SeekBar azimuthDisplay;
    private CountDownTimer animTimer;
    RelativeLayout buttonContainer;

    public EnvironmentHandler(Activity pActivity)
    {
        this.parentActivity = pActivity;
        this.azimuthDisplay = parentActivity.findViewById(R.id.seekBar);

        buttonContainer = parentActivity.findViewById(R.id.augmentedReality);

        initPOIS(0);
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        this.degAzimuth = pAzimuth;
        this.degPitch = pPitch;
        this.degRoll = pRoll;

        azimuthDisplay.setProgress((((int)degAzimuth) + 180)%360); //move North in the middle of the screen.

        updateView();
    }

    public void updatePosition(final float pDecLongitude, final float pDecLatitude, final float pMetersAltitude, final float accuracy) {
        final int numSteps = LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL / 100;
        final float xStepSize = (pDecLongitude - observer.getDecimalLongitude()) / numSteps;
        final float yStepSize = (pDecLatitude - observer.getDecimalLatitude()) / numSteps;

        if (animTimer != null) {
            animTimer.onFinish();
        }

        animTimer = new CountDownTimer(LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL, 100) {
            public void onTick(long millisUntilFinished) {
                observer.updatePOILocation(observer.getDecimalLongitude() + xStepSize,
                        observer.getDecimalLatitude() + yStepSize, pMetersAltitude);
                updateView();
            }

            public void onFinish() {
                observer.updatePOILocation(pDecLongitude, pDecLatitude, pMetersAltitude);
                updateView();
            }
        }.start();
    }

    private void updateView()
    {
        TreeSet<DisplayPOI> visible = new TreeSet();
        //find elements in view and sort them by distance.
        for (PointOfInterest poi: pois)
        {
            float distance = calculateDistance(observer, poi);
            if (distance < MAX_DISTANCE_METERS) {
                float deltaAzimuth = calculateTheoreticalAzimuth(observer, poi);
                float difAngle = diffAngle(deltaAzimuth, degAzimuth);
                visible.add(new DisplayPOI(distance, deltaAzimuth, difAngle, poi));
            } else {
                if (toDisplay.containsKey(poi)) {
                    deleteViewElement(toDisplay.get(poi));
                    toDisplay.remove(poi);
                }
            }
        }

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        for (DisplayPOI ui: visible)
        {
            int size = calculateSizeInDPI(ui.distance);
            int sizeX = (int)(size*0.5);
            int sizeY = size;
            if (Math.abs(ui.difDegAngle) < (VIEW_ANGLE_DEG /2)) {
                float[] pos = getXYPosition(ui.difDegAngle, degPitch, degRoll, sizeX, sizeY);
                float xPos = pos[0];
                float yPos = pos[1];

                if (!toDisplay.containsKey(ui.poi)) {
                    toDisplay.put(ui.poi, addViewElement(xPos, yPos, degRoll, sizeX, sizeY, ui));
                } else {
                    updateViewElement(toDisplay.get(ui.poi), xPos, yPos, degRoll, sizeX, sizeY);
                }
            } else {
                if (toDisplay.containsKey(ui.poi)) {
                    deleteViewElement(toDisplay.get(ui.poi));
                    toDisplay.remove(ui.poi);
                }
            }
        }
    }

    private float[] getXYPosition(float yawDegAngle, float pitch, float roll, float sizeX, float sizeY) {
        float screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        float screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        float absoluteX = ((yawDegAngle * screenWidth) / (VIEW_ANGLE_DEG)) - (sizeX/2);
        float absoluteY = (((pitch * screenHeight) / (VIEW_ANGLE_DEG)) + (screenHeight/2)) - (sizeY/2);
        float radius = absoluteX;

        float[] result = new float[2];

        result[0] = (float)(radius * Math.cos(Math.toRadians(roll))) + (screenWidth/2);
        result[1] = (float)(radius * Math.sin(Math.toRadians(roll))) + absoluteY;

        return result;
    }

    private View addViewElement (float x, float y, float roll, int sizeX, int sizeY, DisplayPOI displayPOI) {
        switch (displayPOI.poi.getType()) {
            case cardinal:
                return addTextView (x, y, roll, sizeX, sizeY, displayPOI);
            default:
                return addViewElementFromTemplate(x, y, roll, sizeX, sizeY, displayPOI);
        }
    }

    private View addViewElementFromTemplate(float x, float y, float roll, int sizeX, int sizeY, DisplayPOI poi) {
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View bt1 = inflater.inflate(R.layout.topo_display_button, null);
        bt1.setOnClickListener(new TopoButtonClickListener(parentActivity, poi));
        buttonContainer.addView(bt1);

        updateViewElement(bt1, x, y, roll, sizeX, sizeY);

        return bt1;
    }

    private View addTextView(float x, float y, float roll, int sizeX, int sizeY, DisplayPOI poi) {

        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View bt1 = inflater.inflate(R.layout.topo_display_button, null);
        bt1.setOnClickListener(new TopoButtonClickListener(parentActivity, poi));
        buttonContainer.addView(bt1);

        updateViewElement(bt1, x, y, roll, sizeX, sizeY);

        return bt1;
    }

    private void deleteViewElement(View button) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        buttonContainer.removeView(button);
    }

    private void updateViewElement(View pButton, float x, float y, float roll, int sizeX, int sizeY) {
        pButton.getLayoutParams().height = sizeY;
        pButton.getLayoutParams().width = sizeX;

        pButton.setX(x);
        pButton.setY(y);
        pButton.setRotation(roll);

        pButton.bringToFront();
        pButton.requestLayout();
    }

    private int calculateSizeInDPI(float distance) {
        float oldRange = (MAX_DISTANCE_METERS - MIN_DISTANCE_METERS);
        int result;
        if (oldRange == 0)
            result =  Math.round(UI_MAX_SCALE);
        else
        {
            float newRange = UI_MIN_SCALE - UI_MAX_SCALE;
            result = Math.round((((distance - MIN_DISTANCE_METERS) * newRange) / oldRange) + UI_MAX_SCALE);
        }

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                result, parentActivity.getResources().getDisplayMetrics());
    }

    private float calculateTheoreticalAzimuth(PointOfInterest obs, PointOfInterest poi) {
        return (float)Math.toDegrees(Math.atan2(poi.getDecimalLongitude() - obs.getDecimalLongitude(),
                poi.getDecimalLatitude() - obs.getDecimalLatitude()));
    }

    /**
     * Calculate distance between 2 coordinates using the haversine algorithm.
     * @param obs
     * @param poi
     * @return
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

        for (int i=0; i< count; ++i) {
            float finalLong = rand.nextFloat() * (maxLong - minLong) + minLong;
            float finalLat = rand.nextFloat() * (maxLat - minLat) + minLat;
            float finalAlt = rand.nextFloat() * (maxAlt - minAlt) + minAlt;

            PointOfInterest tmpPoi = new PointOfInterest(climbing,
                    observer.getDecimalLongitude() + finalLong,
                    observer.getDecimalLatitude() + finalLat,
                    observer.getAltitudeMeters() + finalAlt);
            tmpPoi.updatePOIInfo(100f, "test" + i, "test dectiption not too long though", "trad", "5.12b");
            pois.add(tmpPoi);
        }

        //this is a real one.
        PointOfInterest tmpPoi = new PointOfInterest(climbing,
                -74.33234f,
                45.46703f,
                100f);
        pois.add(tmpPoi);
        tmpPoi = new PointOfInterest(climbing,
                -74.33185f,
                45.46745f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33218f,
                45.46738f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33220f,
                45.46737f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33239f,
                45.46727f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33230f,
                45.46722f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33224f,
                45.46718f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33173f,
                45.46723f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33176f,
                45.46715f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33220f,
                45.46720f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33240f,
                45.46699f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(climbing,
                -74.33237f,
                45.46702f,
                100f);
        pois.add(tmpPoi);
    }
}

