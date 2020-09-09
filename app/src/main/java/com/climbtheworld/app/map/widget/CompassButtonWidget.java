package com.climbtheworld.app.map.widget;

import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.OrientationManager;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class CompassButtonWidget extends MapButtonWidget implements IMapWidget {
    static final String MAP_ROTATION_TOGGLE_BUTTON = "compassButton";
    public static final String keyName = CompassButtonWidget.class.getSimpleName();

    private CompassWidget compass = null;
    private boolean mapRotationEnabled;

    public static void addToActiveWidgets(MapViewWidget mapViewWidget, Map<String, IMapWidget> mapWidgets) {
        View button = mapViewWidget.mapContainer.findViewById(mapViewWidget.parent.getResources().getIdentifier(MAP_ROTATION_TOGGLE_BUTTON, "id", mapViewWidget.parent.getPackageName()));

        if (button != null) {
            mapWidgets.put(keyName, new CompassButtonWidget(mapViewWidget, button));
        }
    }

    private CompassButtonWidget(MapViewWidget mapViewWidget, View widget) {
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
    }

    @Override
    public void onRotate(float deltaAngle) {
        if (Math.abs(mapViewWidget.osmMap.getProjection().getOrientation()) -5 < 5){

        }
    }

    @Override
    public void onOrientationChange(OrientationManager.OrientationEvent event) {
        if (mapRotationEnabled) {
            mapViewWidget.osmMap.setMapOrientation(-(float) event.getAdjusted().x, false);
            mapViewWidget.resetMapProjection();

            if (compass != null) {
                compass.updateOrientation(event);
            }
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
        mapViewWidget.resetMapProjection();

        if (enable) {
            mapRotationEnabled = true;
        } else {
            mapRotationEnabled = false;
            if (compass != null) {
                compass.updateOrientation(new OrientationManager.OrientationEvent());
            }

            mapViewWidget.invalidate(true);
        }
        updateRotationButton(enable);
        mapViewWidget.configs.setBoolean(Configs.ConfigKey.mapViewCompassOrientation, mapRotationEnabled);
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
