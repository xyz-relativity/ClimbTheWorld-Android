package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.ar.opentopo.opentopoar.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by xyz on 11/24/17.
 */

public class EnvironmentHandler {
    private static final float LENS_ANGLE = 20f;
    private float degAzimuth = 0;
    private float degPitch = 0;
    private float degRoll = 0;
    private float screenWidth;
    private float screenHeight;
    private PointOfInterest observer = new PointOfInterest(PointOfInterest.POIType.observer, 0, 0, 0);

    private List<PointOfInterest> pois = new ArrayList<>();
    private Map<PointOfInterest, ImageButton> toDisplay = new HashMap<>();

    private final Activity parentActivity;

    public EnvironmentHandler(Activity pActivity)
    {
        this.parentActivity = pActivity;

        screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        initPOIS(10);
    }

    public void updateOrientation(float pAzimuth, float pPitch, float pRoll) {
        this.degAzimuth = pAzimuth;
        this.degPitch = pPitch;
        this.degRoll = pRoll;

        updateView();
    }

    private void updateView()
    {
        for (PointOfInterest poi: pois)
        {
            float deltaAzimuth = calculateTeoreticalAzimuth(observer, poi);
            float distance = calculateDistance(observer, poi);
            float difAngle = diffAngle(deltaAzimuth, degAzimuth);
            if (Math.abs(difAngle) < LENS_ANGLE) {
                float xPos = ((difAngle + LENS_ANGLE) * screenWidth) / 2*LENS_ANGLE;
                float yPos = 200f;

                if (!toDisplay.containsKey(poi)) {
                    toDisplay.put(poi, addButtons(xPos, yPos, -1*distance));
                } else {
                    updateButton(toDisplay.get(poi), xPos, yPos, -1*distance);
                }
            } else {
                if (toDisplay.containsKey(poi)) {
                    delButtons(toDisplay.get(poi));
                    toDisplay.remove(poi);
                }
            }
        }
    }

    private ImageButton addButtons(float x, float y, float z) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        LayoutInflater inflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageButton bt1 = (ImageButton)inflater.inflate(R.layout.topo_display_button, null);
        buttonContainer.addView(bt1);

        updateButton(bt1, x, y, z);

        return bt1;
    }

    private void delButtons(ImageButton button) {
        RelativeLayout buttonContainer = parentActivity.findViewById(R.id.augmentedReality);
        buttonContainer.removeView(button);
    }

    private void updateButton(ImageButton pButton, float x, float y, float z) {
        int size = calculateSize(z);

        pButton.getLayoutParams().height = size;
        pButton.getLayoutParams().width = size;

        pButton.setX(x);
        pButton.setY(y);
        pButton.requestLayout();
    }

    private int calculateSize(float z) {
        if (z == 0) {
            z = 1;
        }
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                100/((-1)*z),
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

        return (float)Math.sqrt((dX*dX) + (dY*dY));
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
        float minX = -1.0f;
        float maxX = 1.0f;
        Random rand = new Random();

        for (int i=0; i< count; ++i) {
            float finalX = rand.nextFloat() * (maxX - minX) + minX;
            float finalY = rand.nextFloat() * (maxX - minX) + minX;
            float finalZ = rand.nextFloat() * (maxX - minX) + minX;

            PointOfInterest tmpPoi = new PointOfInterest(PointOfInterest.POIType.climbing, finalX, finalY, finalZ);
            pois.add(tmpPoi);
        }
    }
}
