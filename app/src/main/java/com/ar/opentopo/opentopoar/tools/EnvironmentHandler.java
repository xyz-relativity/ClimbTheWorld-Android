package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.ar.opentopo.opentopoar.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * Created by xyz on 11/24/17.
 */

public class EnvironmentHandler {
    private static final float VIEW_ANGLE_DEG = 60f;
    private static final float MAX_DISTANCE_METERS = 50f;
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
    private Map<PointOfInterest, ImageButton> toDisplay = new HashMap<>();

    private final Activity parentActivity;
    private final SeekBar azimuthDisplay;

    public EnvironmentHandler(Activity pActivity)
    {
        this.parentActivity = pActivity;
        this.azimuthDisplay = parentActivity.findViewById(R.id.seekBar);

        initPOIS(1000);
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        this.degAzimuth = pAzimuth;
        this.degPitch = pPitch;
        this.degRoll = pRoll;

        azimuthDisplay.setProgress((((int)degAzimuth) + 180)%360); //move North in the middle of the screen.

        updateView();
    }

    public void updatePosition(float pDecLongitude, float pDecLatitude, float pMetersAltitude, int accuracy) {
        observer.updatePOILocation(pDecLongitude, pDecLatitude, pMetersAltitude);

        updateView();
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
            }
        }

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        for (DisplayPOI ui: visible)
        {
            int size = calculateSizeInDPI(ui.distance);
            int sizeX = (int)(size*0.5);
            int sizeY = size;
            if (Math.abs(ui.difDegAngle) < (VIEW_ANGLE_DEG /2)) {
                float screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
                float screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

                float xPos = (((ui.difDegAngle * screenWidth) / (VIEW_ANGLE_DEG)) + (screenWidth/2)) - (sizeX/2);
                float yPos = (((degPitch * screenHeight) / (VIEW_ANGLE_DEG)) + (screenHeight/2)) - (sizeY/2);

                if (!toDisplay.containsKey(ui.poi)) {
                    toDisplay.put(ui.poi, addButtons(xPos, yPos, sizeX, sizeY, ui));
                } else {
                    updateButton(toDisplay.get(ui.poi), xPos, yPos, sizeX, sizeY);
                }
            } else {
                if (toDisplay.containsKey(ui.poi)) {
                    delButtons(toDisplay.get(ui.poi));
                    toDisplay.remove(ui.poi);
                }
            }
        }
    }

    private ImageButton addButtons(float x, float y, int sizeX, int sizeY, DisplayPOI poi) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageButton bt1 = (ImageButton)inflater.inflate(R.layout.topo_display_button, null);
        bt1.setOnClickListener(new TopoButtonClickListener(parentActivity, poi));
        buttonContainer.addView(bt1);

        updateButton(bt1, x, y, sizeX, sizeY);

        return bt1;
    }

    private void delButtons(ImageButton button) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        buttonContainer.removeView(button);
    }

    private void updateButton(ImageButton pButton, float x, float y, int sizeX, int sizeY) {
        pButton.getLayoutParams().height = sizeY;
        pButton.getLayoutParams().width = sizeX;

        pButton.setX(x);
        pButton.setY(y);
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

            PointOfInterest tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                    observer.getDecimalLongitude() + finalLong,
                    observer.getDecimalLatitude() + finalLat,
                    observer.getAltitudeMeters() + finalAlt);
            tmpPoi.updatePOIInfo(100f, "test" + i, "test dectiption not too long though", "trad", "5.12b");
            pois.add(tmpPoi);
        }

        //this is a real one.
        PointOfInterest tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33234f,
                45.46703f,
                100f);
        pois.add(tmpPoi);
        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33185f,
                45.46745f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33218f,
                45.46738f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33220f,
                45.46737f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33239f,
                45.46727f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33230f,
                45.46722f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33224f,
                45.46718f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33173f,
                45.46723f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33176f,
                45.46715f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33220f,
                45.46720f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33240f,
                45.46699f,
                100f);
        pois.add(tmpPoi);

        tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33237f,
                45.46702f,
                100f);
        pois.add(tmpPoi);
    }
}

