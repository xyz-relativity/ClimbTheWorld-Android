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
        STATIC, USER, AUTO
    }

    static final String MAP_ROTATION_TOGGLE_BUTTON = "compassButton";
    public static final String keyName = CompassButtonMapWidget.class.getSimpleName();
    private final OrientationManager.OrientationEvent userRotationEvent;

    private CompassWidget compass;
    private RotationMode rotationMode = RotationMode.STATIC;

    public static void addToActiveWidgets(MapViewWidget mapViewWidget, Map<String, ButtonMapWidget> mapWidgets) {
        View button = mapViewWidget.mapContainer.findViewById(mapViewWidget.parent.getResources().getIdentifier(MAP_ROTATION_TOGGLE_BUTTON, "id", mapViewWidget.parent.getPackageName()));

        if (button != null) {
            mapWidgets.put(keyName, new CompassButtonMapWidget(mapViewWidget, button));
        }
    }

    private CompassButtonMapWidget(MapViewWidget mapViewWidget, View widget) {
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
        rotationMode = RotationMode.USER;
        updateRotationButton();
        userRotationEvent.global.x = (userRotationEvent.global.x + deltaAngle) % 360;
        compass.updateOrientation(userRotationEvent);
    }

    @Override
    public void onOrientationChange(OrientationManager.OrientationEvent event) {
        if (rotationMode == RotationMode.AUTO) {
            mapViewWidget.osmMap.setMapOrientation(-(float) event.getAdjusted().x, false);

            compass.updateOrientation(event);
        } else {
            mapViewWidget.obsLocationMarker.setRotation(-(float) event.getAdjusted().x);
        }
        mapViewWidget.invalidate(false);
    }

    @Override
    public void onLocationChange(GeoPoint location) {

    }

    private void toggleRotationMode() {
        if (rotationMode == RotationMode.STATIC) {
            setRotationMode(true);
            rotationMode = RotationMode.AUTO;
            return;
        }

        if (rotationMode == RotationMode.USER) {
            setRotationMode(false);
            rotationMode = RotationMode.STATIC;
            return;
        }

        if (rotationMode == RotationMode.AUTO) {
            setRotationMode(false);
            rotationMode = RotationMode.STATIC;
            return;
        }
    }

    public void setRotationMode(boolean enable) {
        mapViewWidget.obsLocationMarker.setRotation(0f);
        mapViewWidget.osmMap.setMapOrientation(0f, false);

        if (enable) {
            rotationMode = RotationMode.AUTO;
        } else {
            rotationMode = RotationMode.STATIC;
            if (compass != null) {
                compass.updateOrientation(new OrientationManager.OrientationEvent());
            }
        }
        updateRotationButton();
        mapViewWidget.configs.setBoolean(Configs.ConfigKey.mapViewCompassOrientation, rotationMode == RotationMode.AUTO);
        mapViewWidget.invalidate(true);
    }

    private void updateRotationButton() {
        ImageView img = (ImageView) widget;
        if (img != null) {
            if (rotationMode == RotationMode.USER) {
                img.setColorFilter(Color.parseColor("#99ffffff"));
            } else {
                img.setColorFilter(null);
            }
        }
    }
}
