package com.climbtheworld.app.map.widget;

import com.climbtheworld.app.sensors.OrientationManager;

import org.osmdroid.util.GeoPoint;

public interface IMapWidget {
    void onRotate(float deltaAngle);
    void onOrientationChange(OrientationManager.OrientationEvent event);
    void onLocationChange(GeoPoint location);
}
