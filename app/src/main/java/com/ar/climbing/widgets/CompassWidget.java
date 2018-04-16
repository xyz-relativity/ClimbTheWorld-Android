package com.ar.climbing.widgets;

import android.view.View;

import com.ar.climbing.sensors.IOrientationListener;

/**
 * Created by xyz on 1/31/18.
 */

public class CompassWidget implements IOrientationListener {
    private final View compass;

    public CompassWidget(View compassContainer) {
        this.compass = compassContainer;
    }

    @Override
    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        compass.setRotation(-(float)pAzimuth);
    }
}