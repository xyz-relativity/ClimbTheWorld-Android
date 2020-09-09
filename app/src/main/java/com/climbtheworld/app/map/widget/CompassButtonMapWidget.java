package com.climbtheworld.app.map.widget;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.OrientationManager;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class CompassButtonMapWidget extends ButtonMapWidget {
    private enum RotationMode {
        STATIC, AUTO, USER;
    }

    static final String MAP_ROTATION_TOGGLE_BUTTON = "compassButton";
    public static final String keyName = CompassButtonMapWidget.class.getSimpleName();
    private final OrientationManager.OrientationEvent userRotationEvent;

    private CompassWidget compass;
    private RotationMode rotationMode = RotationMode.STATIC;

    public static void addToActiveWidgets(MapViewWidget mapViewWidget, Map<String, ButtonMapWidget> mapWidgets) {
        ImageView button = mapViewWidget.mapContainer.findViewById(mapViewWidget.parent.getResources().getIdentifier(MAP_ROTATION_TOGGLE_BUTTON, "id", mapViewWidget.parent.getPackageName()));

        if (button != null) {
            mapWidgets.put(keyName, new CompassButtonMapWidget(mapViewWidget, button));
        }
    }

    private CompassButtonMapWidget(MapViewWidget mapViewWidget, ImageView widget) {
        super(mapViewWidget, widget);

        widget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRotationMode();
            }
        });

        compass = new CompassWidget(widget);
        userRotationEvent = new OrientationManager.OrientationEvent();
    }

    @Override
    public void onRotate(float deltaAngle) {
        if (rotationMode == RotationMode.USER) {
            userRotationEvent.global.x = ( (- mapViewWidget.osmMap.getMapOrientation()) + deltaAngle) % 360;
            compass.updateOrientation(userRotationEvent);
        }
    }

    @Override
    public void onOrientationChange(OrientationManager.OrientationEvent event) {
        if (rotationMode == RotationMode.AUTO) {
            mapViewWidget.osmMap.setMapOrientation(-(float) event.getAdjusted().x, false);
            mapViewWidget.obsLocationMarker.setRotation(0f);

            compass.updateOrientation(event);
        } else {
            mapViewWidget.obsLocationMarker.setRotation(-(float) (event.getAdjusted().x + mapViewWidget.osmMap.getMapOrientation()));
        }
        mapViewWidget.invalidate(false);
    }

    @Override
    public void onLocationChange(GeoPoint location) {

    }

    private void toggleRotationMode() {
        int index = rotationMode.ordinal();
        setState(RotationMode.values()[(index + 1) % RotationMode.values().length]);
    }

    public void setAutoRotationMode(boolean enable) {
        if (enable) {
            setState(RotationMode.AUTO);
        } else {
            setState(RotationMode.STATIC);
        }
        mapViewWidget.invalidate(true);
    }

    private void setState(RotationMode state) {
        rotationMode = state;
        switch (rotationMode) {
            case AUTO:
                mapViewWidget.setRotateGesture(false);
                widget.setColorFilter(null);
                break;
            case USER:
                mapViewWidget.setRotateGesture(true);
                widget.setColorFilter(Color.parseColor("#99222222"));
                break;
            case STATIC:
                mapViewWidget.obsLocationMarker.setRotation(0f);
                mapViewWidget.osmMap.setMapOrientation(0f, false);
                mapViewWidget.setRotateGesture(false);
                compass.updateOrientation(new OrientationManager.OrientationEvent());
                widget.setColorFilter(null);
                break;
        }
        mapViewWidget.configs.setBoolean(Configs.ConfigKey.mapViewCompassOrientation, rotationMode == RotationMode.AUTO);
    }
}
