package com.climbtheworld.app.map.widget;

import android.view.View;

import com.climbtheworld.app.sensors.OrientationManager;

import org.osmdroid.util.GeoPoint;

public abstract class ButtonMapWidget {
    final MapViewWidget mapViewWidget;
    final View widget;

    protected ButtonMapWidget(MapViewWidget mapViewWidget, View widget) {
        this.mapViewWidget = mapViewWidget;
        this.widget = widget;
    }

    public abstract void onRotate(float deltaAngle);
    public abstract void onOrientationChange(OrientationManager.OrientationEvent event);
    public abstract void onLocationChange(GeoPoint location);
}
