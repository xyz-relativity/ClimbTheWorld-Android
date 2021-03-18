package com.climbtheworld.app.sensors.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xyz on 12/6/17.
 */

public class DeviceLocationManager implements FuseLocationProvider.LocationEvent {
	private AppCompatActivity parent;
	private final List<ILocationListener> eventsHandler = new ArrayList<>();
	private final FuseLocationProvider fusedLocationManager;

	public DeviceLocationManager(AppCompatActivity parent, int intervalMs, ILocationListener pEventsHandler) {
		this.parent = parent;
		addListener(pEventsHandler);

		Location lastLocation;
		Configs configs = Configs.instance(parent);
		lastLocation = new Location(LocationManager.PASSIVE_PROVIDER);

		lastLocation.setLatitude(configs.getFloat(Configs.ConfigKey.virtualCameraDegLat));
		lastLocation.setLongitude(configs.getFloat(Configs.ConfigKey.virtualCameraDegLon));

		lastLocation.setAccuracy(999);
		lastLocation.setTime(SystemClock.elapsedRealtimeNanos());
		onLocationChanged(lastLocation);

		fusedLocationManager = new FuseLocationProvider(parent, intervalMs, this);
	}

	public void addListener(ILocationListener... pEventsHandler) {
		for (ILocationListener i : pEventsHandler) {
			if (!eventsHandler.contains(i)) {
				eventsHandler.add(i);
			}
		}

	}

	public void onResume() {
		fusedLocationManager.onResume();
	}

	public void onPause() {
		fusedLocationManager.onPause();
	}

	@Override
	public void onLocationChanged(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		double elev = location.getAltitude();
		double accuracy = location.getAccuracy();

		for (ILocationListener client : eventsHandler) {
			client.updatePosition(lat, lon, elev, accuracy);
		}
	}
}
