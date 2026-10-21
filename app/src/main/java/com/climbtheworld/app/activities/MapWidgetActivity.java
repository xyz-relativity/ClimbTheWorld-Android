package com.climbtheworld.app.activities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;

/**
 * An activity drawing the map, which follows the activity's lifecycle and the device's position
 * and heading: the subclass makes the widget in onCreate, and starts its sensors after
 * super.onResume and stops them before super.onPause.
 */
abstract class MapWidgetActivity extends AppCompatActivity
		implements IOrientationListener, ILocationListener {
	protected MapLibreMapWidget mapWidget;

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		mapWidget.onOrientationChange(event.getAdjusted(), event);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude,
	                           double accuracy) {
		Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
		mapWidget.onLocationChange(new MapCoordinate(pDecLatitude, pDecLongitude, pMetersAltitude));
	}

	@Override
	protected void onStart() {
		super.onStart();
		mapWidget.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);
		mapWidget.onResume();
	}

	@Override
	protected void onPause() {
		mapWidget.onPause();
		Globals.onPause(this);
		super.onPause();
	}

	@Override
	protected void onStop() {
		mapWidget.onStop();
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapWidget.onLowMemory();
	}

	@Override
	protected void onDestroy() {
		mapWidget.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		mapWidget.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
	}
}
