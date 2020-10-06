package com.climbtheworld.app.map.widget;

import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.sensors.orientation.OrientationManager;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class LocationButtonMapWidget extends ButtonMapWidget {
    static final String MAP_CENTER_ON_GPS_BUTTON = "mapCenterOnGpsButton";
    public static final String keyName = LocationButtonMapWidget.class.getSimpleName();

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
                if (!MapViewWidget.staticState.mapFollowObserver) {
                    setMapAutoFollow(true);
                }
            }
        });
    }

    @Override
    public void onTouch(MotionEvent motionEvent) {
        if ((motionEvent.getAction() == MotionEvent.ACTION_MOVE) && MapViewWidget.staticState.mapFollowObserver) {
            setMapAutoFollow(false);
        }
    }

    @Override
    public void onOrientationChange(OrientationManager.OrientationEvent event) {

    }

    @Override
    public void onLocationChange(GeoPoint location) {
        mapViewWidget.obsLocationMarker.getPosition().setCoords(location.getLatitude(), location.getLongitude());
        mapViewWidget.obsLocationMarker.getPosition().setAltitude(location.getAltitude());

        if (MapViewWidget.staticState.mapFollowObserver) {
            centerOnObserver();
        }
        mapViewWidget.invalidate(false);
    }

    public void setMapAutoFollow(boolean enable) {
        if (MapViewWidget.staticState.mapFollowObserver != enable) {
            if (enable) {
                MapViewWidget.staticState.mapFollowObserver = true;
                centerOnObserver();
            } else {
                MapViewWidget.staticState.mapFollowObserver = false;
            }
        }
        updateAutoFollowButton();
    }

    private void updateAutoFollowButton() {
        ImageView img = (ImageView) widget;
        if (img != null) {
            if (MapViewWidget.staticState.mapFollowObserver) {
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
