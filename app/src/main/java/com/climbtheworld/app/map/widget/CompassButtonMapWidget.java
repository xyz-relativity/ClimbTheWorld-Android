package com.climbtheworld.app.map.widget;

import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.OrientationManager;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class CompassButtonMapWidget extends ButtonMapWidget {
    static final String MAP_ROTATION_TOGGLE_BUTTON = "compassButton";
    public static final String keyName = CompassButtonMapWidget.class.getSimpleName();
    private final OrientationManager.OrientationEvent userRotationEvent;

    private CompassWidget compass;
    private boolean mapRotationEnabled;

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
                if (view.getTag() == "on") {
                    setRotationMode(false);
                } else if (view.getTag() == "manual") {
                    setRotationMode(false);
                } else {
                    setRotationMode(true);
                }
            }
        });

        compass = new CompassWidget(widget);
        userRotationEvent = new OrientationManager.OrientationEvent();
    }

    @Override
    public void onRotate(float deltaAngle) {
        setRotationMode(false);
        userRotationEvent.global.x = (userRotationEvent.global.x + deltaAngle) % 360;
        compass.updateOrientation(userRotationEvent);
    }

    @Override
    public void onOrientationChange(OrientationManager.OrientationEvent event) {
        if (mapRotationEnabled) {
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

    public void setRotationMode(boolean enable) {
        mapViewWidget.obsLocationMarker.setRotation(0f);
        mapViewWidget.osmMap.setMapOrientation(0f, false);


        if (enable) {
            mapRotationEnabled = true;
        } else {
            mapRotationEnabled = false;
            if (compass != null) {
                compass.updateOrientation(new OrientationManager.OrientationEvent());
            }
        }
        updateRotationButton(enable);
        mapViewWidget.configs.setBoolean(Configs.ConfigKey.mapViewCompassOrientation, mapRotationEnabled);
        mapViewWidget.invalidate(true);
    }

    private void updateRotationButton(boolean enable) {
        ImageView img = (ImageView) widget;
        if (img != null) {
            if (enable) {
                img.setColorFilter(null);
                img.setTag("on");
            } else {
                img.setColorFilter(null);
                img.setTag("");
            }
        }
    }
}
