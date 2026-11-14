package com.climbtheworld.app.sensors.location;

import android.content.Context;
import android.location.Location;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by xyz on 12/6/17.
 */

public class DeviceLocationManager implements FuseLocationProvider.LocationEvent {
	private ILocationListener eventsHandler;
	private final FuseLocationProvider fusedLocationManager;
	private final Context context;

	public DeviceLocationManager(AppCompatActivity parent, int intervalMs) {
		context = parent.getApplicationContext();
		fusedLocationManager = new FuseLocationProvider(parent, intervalMs);
	}

	public void requestUpdates(ILocationListener eventsHandler) {
		this.eventsHandler = eventsHandler;
		fusedLocationManager.requestUpdates(this);
	}

	public void stopUpdates() {
		fusedLocationManager.stopUpdates();
	}

	@Override
	public void onLocationChanged(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		double elev = SeaLevelAltitude.getAltitude(context, location);
		double accuracy = location.getAccuracy();

		if (eventsHandler != null) {
			eventsHandler.updatePosition(lat, lon, elev, accuracy);
		}
	}
}
