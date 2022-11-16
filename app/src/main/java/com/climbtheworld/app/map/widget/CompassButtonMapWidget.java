package com.climbtheworld.app.map.widget;

import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Vector4d;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.gestures.RotationGestureDetector;

import java.util.Map;

public class CompassButtonMapWidget extends ButtonMapWidget implements RotationGestureDetector.RotationListener {
	public enum RotationMode {
		STATIC, AUTO, USER,
	}


	private static final long DELTA_TIME = 25L;
	private static final int THRESHOLD_ANGLE = 25;
	static final String MAP_ROTATION_TOGGLE_BUTTON = "compassButton";
	public static final String KEY_NAME = CompassButtonMapWidget.class.getSimpleName();

	private long timeLastSet = 0L;
	private float currentAngle = 0f;

	private final OrientationManager.OrientationEvent userRotationEvent;
	private final RotationGestureDetector roationDetector = new RotationGestureDetector(this);

	private final CompassWidget compass;
	private RotationMode rotationMode = RotationMode.STATIC;

	public static void addToActiveWidgets(MapViewWidget mapViewWidget, Map<String, ButtonMapWidget> mapWidgets) {
		ImageView button = mapViewWidget.mapContainer.findViewById(mapViewWidget.parentRef.get().getResources().getIdentifier(MAP_ROTATION_TOGGLE_BUTTON, "id", mapViewWidget.parentRef.get().getPackageName()));

		if (button != null) {
			mapWidgets.put(KEY_NAME, new CompassButtonMapWidget(mapViewWidget, button));
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

		compass = new CompassWidget(widget, false);
		userRotationEvent = new OrientationManager.OrientationEvent();
	}

	@Override
	public void onTouch(MotionEvent motionEvent) {
		if (motionEvent.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
			currentAngle = 0f;
		}
		roationDetector.onTouch(motionEvent);
	}

	@Override
	public void onRotate(float deltaAngle) {
		if (rotationMode == RotationMode.USER) {
			currentAngle = (currentAngle + deltaAngle) % 360;
			if (System.currentTimeMillis() - DELTA_TIME > timeLastSet) {
				timeLastSet = System.currentTimeMillis();
				mapViewWidget.osmMap.setMapOrientation(mapViewWidget.osmMap.getMapOrientation() + currentAngle);
				mapViewWidget.resetMapProjection();
				currentAngle = 0f;
			}

			userRotationEvent.screen.x = ((-mapViewWidget.osmMap.getMapOrientation()) + deltaAngle) % 360;
			compass.updateOrientation(userRotationEvent.screen);
		} else if (Math.abs(currentAngle) > THRESHOLD_ANGLE) {
			setState(RotationMode.USER);
		} else {
			currentAngle = (currentAngle + deltaAngle) % 360;
		}
	}

	@Override
	public void onOrientationChange(Vector4d event) {
		if (rotationMode == RotationMode.AUTO) {
			mapViewWidget.osmMap.setMapOrientation(-(float) event.x, false);
			mapViewWidget.obsLocationMarker.setRotation(0f);

			compass.updateOrientation(event);
		} else {
			mapViewWidget.obsLocationMarker.setRotation(-(float) (event.x + mapViewWidget.osmMap.getMapOrientation()));
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

	public void setAutoRotationMode(RotationMode enable) {
		setState(enable);
		mapViewWidget.invalidate(true);
	}

	private void setState(RotationMode state) {
		rotationMode = state;
		switch (rotationMode) {
			case AUTO:
				widget.setImageDrawable(ResourcesCompat.getDrawable(mapViewWidget.parentRef.get().getResources(), R.drawable.ic_compass, null));
				break;
			case USER:
				widget.setImageDrawable(ResourcesCompat.getDrawable(mapViewWidget.parentRef.get().getResources(), R.drawable.ic_compass_user, null));
				break;
			case STATIC:
				mapViewWidget.obsLocationMarker.setRotation(0f);
				mapViewWidget.osmMap.setMapOrientation(0f, false);
				compass.updateOrientation(new Vector4d());
				widget.setImageDrawable(ResourcesCompat.getDrawable(mapViewWidget.parentRef.get().getResources(), R.drawable.ic_compass, null));
				break;
		}
		mapViewWidget.saveRotationMode(rotationMode.ordinal());
	}
}
