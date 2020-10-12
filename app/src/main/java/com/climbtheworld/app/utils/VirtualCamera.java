package com.climbtheworld.app.utils;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.database.GeoNode;

/**
 * Created by xyz on 12/26/17.
 */

public class VirtualCamera extends GeoNode implements ILocationListener, IOrientationListener {
	public double degAzimuth = 0;
	public double degPitch = 0;
	public double degRoll = 0;
	public Vector2d fieldOfViewDeg = new Vector2d(60f, 40f);
	public double screenRotation = 0;

	public VirtualCamera(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude) {
		super(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
	}

	public void onPause(AppCompatActivity parent) {
		saveLocation(Configs.instance(parent));
	}

	public void onResume(AppCompatActivity parent) {
		loadLocation(Configs.instance(parent));
	}

	private void saveLocation(Configs configs) {
		configs.setFloat(Configs.ConfigKey.virtualCameraDegLat, (float) decimalLatitude);
		configs.setFloat(Configs.ConfigKey.virtualCameraDegLon, (float) decimalLongitude);
	}

	private void loadLocation(Configs configs) {
		decimalLatitude = configs.getFloat(Configs.ConfigKey.virtualCameraDegLat);
		decimalLongitude = configs.getFloat(Configs.ConfigKey.virtualCameraDegLon);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		degAzimuth = event.camera.x;
		degPitch = event.camera.y;
		degRoll = event.camera.z;
	}
}
