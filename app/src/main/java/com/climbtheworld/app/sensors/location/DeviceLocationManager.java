package com.climbtheworld.app.sensors.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;

/**
 * Created by xyz on 12/6/17.
 */

public class DeviceLocationManager implements FuseLocationProvider.LocationEvent {
	private ILocationListener eventsHandler;
	private final FuseLocationProvider fusedLocationManager;

	public DeviceLocationManager(AppCompatActivity parent, int intervalMs) {

		Location lastLocation;
		Configs configs = Configs.instance(parent);
		lastLocation = new Location(LocationManager.PASSIVE_PROVIDER);

		lastLocation.setLatitude(configs.getFloat(Configs.ConfigKey.virtualCameraDegLat));
		lastLocation.setLongitude(configs.getFloat(Configs.ConfigKey.virtualCameraDegLon));

		lastLocation.setAccuracy(999);
		lastLocation.setTime(SystemClock.elapsedRealtimeNanos());
		onLocationChanged(lastLocation);

		fusedLocationManager = new FuseLocationProvider(parent, intervalMs);
	}

	public void requestUpdates(ILocationListener eventsHandler) {
		this.eventsHandler = eventsHandler;
		fusedLocationManager.requestUpdates(this);
	}

	public void removeUpdates() {
		fusedLocationManager.removeUpdates();
	}

	@Override
	public void onLocationChanged(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		double elev = location.getAltitude();
		double accuracy = location.getAccuracy();

		if (eventsHandler != null) {
			eventsHandler.updatePosition(lat, lon, elev, accuracy);
		}
	}
}
