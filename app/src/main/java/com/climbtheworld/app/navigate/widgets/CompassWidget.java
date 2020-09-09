package com.climbtheworld.app.navigate.widgets;

import android.view.View;

import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.OrientationManager;

/**
 * Created by xyz on 1/31/18.
 */

public class CompassWidget implements IOrientationListener {
    private final View compass;

    public CompassWidget(View compassContainer) {
        this.compass = compassContainer;
    }

    public double getOrientation() {
        return compass.getRotation();
    }

    @Override
    public void updateOrientation(OrientationManager.OrientationEvent event) {
        compass.setRotation(-(float) event.global.x);
    }
}
