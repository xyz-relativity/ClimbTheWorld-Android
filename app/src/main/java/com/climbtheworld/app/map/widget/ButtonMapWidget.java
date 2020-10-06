package com.climbtheworld.app.map.widget;

import android.view.MotionEvent;
import android.widget.ImageView;

import com.climbtheworld.app.sensors.orientation.OrientationManager;

import org.osmdroid.util.GeoPoint;

public abstract class ButtonMapWidget {
    final MapViewWidget mapViewWidget;
    final ImageView widget;

    protected ButtonMapWidget(MapViewWidget mapViewWidget, ImageView widget) {
        this.mapViewWidget = mapViewWidget;
        this.widget = widget;
    }

    public abstract void onTouch(MotionEvent motionEvent);
    public abstract void onOrientationChange(OrientationManager.OrientationEvent event);
    public abstract void onLocationChange(GeoPoint location);
}
