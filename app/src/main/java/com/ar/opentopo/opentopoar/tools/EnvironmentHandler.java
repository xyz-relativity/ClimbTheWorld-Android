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
    class ToDisplay implements Comparable
    {
        public float distance = 0;
        public PointOfInterest poi;
        public float deltaDegAzimuth = 0;
        public float difDegAngle = 0;

        public ToDisplay(float pDistance, float pDeltaDegAzimuth, float pDiffDegAngle,PointOfInterest pPoi) {
            this.distance = pDistance;
            this.poi = pPoi;
            this.deltaDegAzimuth = pDeltaDegAzimuth;
            this.difDegAngle = pDiffDegAngle;
        }

        @Override
        public int compareTo(@NonNull Object o) {
            if (o instanceof ToDisplay) {
                if (this.distance > ((ToDisplay) o).distance) return 1;
                if (this.distance < ((ToDisplay) o).distance) return -1;
                else return 0;
            }
            return 0;
        }
    }

    private static final float LENS_ANGLE = 30f;
    private static final float MAX_DISTANCE = 5f;
    private static final float UI_SCALE_FACTOR = 50f;
    private static final float METERS_PER_DEGREE_AT_EQUATOR = 111319.9f;

    private float degAzimuth = 0;
    private float degPitch = 0;
    private float degRoll = 0;
    private float screenWidth;
    private float screenHeight;
    private PointOfInterest observer = new PointOfInterest(PointOfInterest.POIType.observer, 0, 0, 0);

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

        initPOIS(40);
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        this.degAzimuth = pAzimuth;
        this.degPitch = pPitch;
        this.degRoll = pRoll;

        azimuthDisplay.setProgress((int)degAzimuth);

        updateView();
    }

    public void updatePosition(float pDecLongitude, float pDecLatitude, float pMetersAltitude, int accuracy) {
        observer.updatePOI(pDecLongitude, pDecLatitude, pMetersAltitude);

        updateView();
    }

    private void updateView()
    {
        TreeSet<ToDisplay> visible = new TreeSet();
        //find elements in view
        for (PointOfInterest poi: pois)
        {
            float distance = calculateDistance(observer, poi);
            if (distance < MAX_DISTANCE) {
                float deltaAzimuth = calculateTeoreticalAzimuth(observer, poi);
                float difAngle = diffAngle(deltaAzimuth, degAzimuth);
                visible.add(new ToDisplay(distance, deltaAzimuth, difAngle, poi));
            }
        }

        for (ToDisplay ui: visible)
        {
            int size = calculateSize(ui.distance);
            if (Math.abs(ui.difDegAngle) < LENS_ANGLE) {
                float xPos = (((ui.difDegAngle + LENS_ANGLE) * screenWidth) / (2*LENS_ANGLE))-(size/2);
                float yPos = screenHeight/2;

                if (!toDisplay.containsKey(ui.poi)) {
                    toDisplay.put(ui.poi, addButtons(xPos, yPos, size, ui.poi));
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

    private ImageButton addButtons(float x, float y, float size, PointOfInterest poi) {
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
                (MAX_DISTANCE/z) * UI_SCALE_FACTOR,
                parentActivity.getResources().getDisplayMetrics());
    }

    private float calculateTeoreticalAzimuth(PointOfInterest obs, PointOfInterest poi) {
        float dX = poi.getDecimalLatitude() - obs.getDecimalLatitude();
        float dY = poi.getDecimalLongitude() - obs.getDecimalLongitude();

        double phiAngle;
        double tanPhi;

        tanPhi = Math.abs(dY / dX);
        phiAngle = Math.atan(tanPhi);
        phiAngle = Math.toDegrees(phiAngle);

        if (dX > 0 && dY > 0) { // I quater
            return (float)phiAngle;
        } else if (dX < 0 && dY > 0) { // II
            return (float)(180 - phiAngle);
        } else if (dX < 0 && dY < 0) { // III
            return (float)(180 + phiAngle);
        } else if (dX > 0 && dY < 0) { // IV
            return (float)(360 - phiAngle);
        }

        return (float)phiAngle;
    }

    private float calculateDistance(PointOfInterest obs, PointOfInterest poi) {
        float dX = poi.getDecimalLatitude() - obs.getDecimalLatitude();
        float dY = poi.getDecimalLongitude() - obs.getDecimalLongitude();

        float distInDeg = (float)Math.sqrt((dX*dX) + (dY*dY));
        return distInDeg*(METERS_PER_DEGREE_AT_EQUATOR * (float)Math.cos(obs.getDecimalLatitude()*(Math.PI / 100)));
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
    }
}

