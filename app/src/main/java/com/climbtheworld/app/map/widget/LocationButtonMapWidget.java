package com.climbtheworld.app.map.widget;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.sensors.OrientationManager;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class LocationButtonMapWidget extends ButtonMapWidget {
    static final String MAP_CENTER_ON_GPS_BUTTON = "mapCenterOnGpsButton";
    public static final String keyName = LocationButtonMapWidget.class.getSimpleName();
    private boolean mapAutoCenter = true;

    public static void addToActiveWidgets(MapViewWidget mapViewWidget, Map<String, ButtonMapWidget> mapWidgets) {
        ImageView button = mapViewWidget.mapContainer.findViewById(mapViewWidget.parent.getResources().getIdentifier(MAP_CENTER_ON_GPS_BUTTON, "id", mapViewWidget.parent.getPackageName()));

        if (button != null) {
            mapWidgets.put(keyName, new LocationButtonMapWidget(mapViewWidget, button));
        }
    }

    public LocationButtonMapWidget(MapViewWidget mapViewWidget, ImageView widget) {
        super(mapViewWidget, widget);

        widget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mapAutoCenter) {
                    setMapAutoFollow(true);
                }
            }
        });
    }

    @Override
    public void onRotate(float deltaAngle) {

    }

    @Override
    public void onOrientationChange(OrientationManager.OrientationEvent event) {

    }

    @Override
    public void onLocationChange(GeoPoint location) {
        mapViewWidget.obsLocationMarker.getPosition().setCoords(location.getLatitude(), location.getLongitude());
        mapViewWidget.obsLocationMarker.getPosition().setAltitude(location.getAltitude());

        if (mapAutoCenter) {
            centerOnObserver();
        }
        mapViewWidget.invalidate(false);
    }

    public void setMapAutoFollow(boolean enable) {
        if (mapAutoCenter != enable) {
            if (enable) {
                mapAutoCenter = true;
                centerOnObserver();
            } else {
                mapAutoCenter = false;
            }
            updateAutoFollowButton();
        }
    }

    private void updateAutoFollowButton() {
        ImageView img = (ImageView) widget;
        if (img != null) {
            if (mapAutoCenter) {
                img.setColorFilter(null);
            } else {
                img.setColorFilter(Color.parseColor("#aaffffff"));
            }
        }
    }

    private void centerOnObserver() {
        mapViewWidget.centerOnGoePoint(mapViewWidget.obsLocationMarker.getPosition());
    }
}
