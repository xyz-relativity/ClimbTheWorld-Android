package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
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
    private static final float LENS_ANGLE = 30f;
    private static final float MAX_DISTANCE_METERS = 100f;
    private static final float UI_SCALE_FACTOR = 50f;
    private static final double EARTH_RADIUS_KM = 6371;

    private float degAzimuth = 0;
    private float degPitch = 0;
    private float degRoll = 0;
    private float screenWidth;
    private float screenHeight;
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

        screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        this.azimuthDisplay = parentActivity.findViewById(R.id.seekBar);

        initPOIS(0);
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        this.degAzimuth = pAzimuth;
        this.degPitch = pPitch;
        this.degRoll = pRoll;

        azimuthDisplay.setProgress((((int)degAzimuth) + 180)%360); //move North in the middle of the screen.

        updateView();
    }

    public void updatePosition(float pDecLongitude, float pDecLatitude, float pMetersAltitude, int accuracy) {
        observer.updatePOI(pDecLongitude, pDecLatitude, pMetersAltitude);

        updateView();
    }

    private void updateView()
    {
        TreeSet<DisplayPOI> visible = new TreeSet();
        //find elements in view and sort them by distance.
        for (PointOfInterest poi: pois)
        {
            float distance = calculateDistance(observer, poi);
            System.out.println(distance);
            if (distance < MAX_DISTANCE_METERS) {
                float deltaAzimuth = calculateTheoreticalAzimuth(observer, poi);
                float difAngle = diffAngle(deltaAzimuth, degAzimuth);
                visible.add(new DisplayPOI(distance, deltaAzimuth, difAngle, poi));
            }
        }

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        for (DisplayPOI ui: visible)
        {
            int size = calculateSize(ui.distance);
            if (Math.abs(ui.difDegAngle) < LENS_ANGLE) {
                float xPos = (((ui.difDegAngle + LENS_ANGLE) * screenWidth) / (2*LENS_ANGLE))-(size/2);
                float yPos = screenHeight/2;

                if (!toDisplay.containsKey(ui.poi)) {
                    toDisplay.put(ui.poi, addButtons(xPos, yPos, size, ui));
                } else {
                    updateButton(toDisplay.get(ui.poi), xPos, yPos, size);
                }
            } else {
                if (toDisplay.containsKey(ui.poi)) {
                    delButtons(toDisplay.get(ui.poi));
                    toDisplay.remove(ui.poi);
                }
            }
        }
    }

    private ImageButton addButtons(float x, float y, float size, DisplayPOI poi) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageButton bt1 = (ImageButton)inflater.inflate(R.layout.topo_display_button, null);
        bt1.setOnClickListener(new TopoButtonClickListener(parentActivity, poi));
        buttonContainer.addView(bt1);

        updateButton(bt1, x, y, size);

        return bt1;
    }

    private void delButtons(ImageButton button) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        buttonContainer.removeView(button);
    }

    private void updateButton(ImageButton pButton, float x, float y, float size) {
        pButton.getLayoutParams().height = Math.round(size);
        pButton.getLayoutParams().width = Math.round(size);

        pButton.setX(x);
        pButton.setY(y);
        pButton.bringToFront();
        pButton.requestLayout();
    }

    private int calculateSize(float z) {
        if (z == 0) {
            z = 1;
        }
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                (MAX_DISTANCE_METERS /z) * UI_SCALE_FACTOR,
                parentActivity.getResources().getDisplayMetrics());
    }

    private float calculateTheoreticalAzimuth(PointOfInterest obs, PointOfInterest poi) {
        float azimuth = (float)Math.toDegrees(Math.atan2(poi.getDecimalLongitude() - obs.getDecimalLongitude(),
                poi.getDecimalLatitude() - obs.getDecimalLatitude()));
        return azimuth;
    }

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
        float d = Math.abs(a - b) % 360;
        float r = d > 180 ? 360 - d : d;

        int sign = (a - b >= 0 && a - b <= 180) || (a - b <=-180 && a- b>= -360) ? 1 : -1;
        r *= sign;

        return r;
    }

    //debug code
    private void initPOIS(int count) {
        float minLat = -0.0001f;
        float maxLat = 0.0001f;

        float minLong = -0.0001f;
        float maxLong = 0.0001f;

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
                    observer.getMetersAltitude() + finalAlt);
            pois.add(tmpPoi);
        }

        //this is a real one.
        PointOfInterest tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing,
                -74.33234f,
                45.46703f,
                100f);
        pois.add(tmpPoi);
    }
}

