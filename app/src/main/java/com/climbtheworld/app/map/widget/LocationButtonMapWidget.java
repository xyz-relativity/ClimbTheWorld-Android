package com.climbtheworld.app.map.widget;

import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.utils.Vector4d;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class LocationButtonMapWidget extends ButtonMapWidget {
	static final String MAP_CENTER_ON_GPS_BUTTON = "mapCenterOnGpsButton";
	public static final String keyName = LocationButtonMapWidget.class.getSimpleName();

	public static void addToActiveWidgets(MapViewWidget mapViewWidget, Map<String, ButtonMapWidget> mapWidgets) {
		ImageView button = mapViewWidget.mapContainer.findViewById(mapViewWidget.parentRef.get().getResources().getIdentifier(MAP_CENTER_ON_GPS_BUTTON, "id", mapViewWidget.parentRef.get().getPackageName()));

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
	public void onOrientationChange(Vector4d event) {

	}

	@Override
	public void onLocationChange(GeoPoint location) {
		mapViewWidget.obsLocationMarker.getPosition().setCoords(location.getLatitude(), location.getLongitude());
		mapViewWidget.obsLocationMarker.getPosition().setAltitude(location.getAltitude());
		mapViewWidget.invalidate(false);
	}

	public void setMapAutoFollow(boolean enable) {
		if (MapViewWidget.staticState.mapFollowObserver != enable) {
			if (enable) {
				MapViewWidget.staticState.mapFollowObserver = true;
				mapViewWidget.centerOnObserver();
			} else {
				MapViewWidget.staticState.mapFollowObserver = false;
			}
		}
		updateAutoFollowButton();
	}

	private void updateAutoFollowButton() {
		ImageView img = widget;
		if (img != null) {
			if (MapViewWidget.staticState.mapFollowObserver) {
				img.setColorFilter(null);
			} else {
				img.setColorFilter(Color.parseColor("#aaffffff"));
			}
		}
	}
}
