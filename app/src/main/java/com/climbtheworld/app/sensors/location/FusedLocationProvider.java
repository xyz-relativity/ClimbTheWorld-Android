package com.climbtheworld.app.sensors.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.climbtheworld.app.configs.Configs;

import java.util.concurrent.TimeUnit;

public class FusedLocationProvider implements LocationListener {
	private static final float MINIMUM_DISTANCE_METERS = 1f;

	private final Configs configs;
	private final LocationEvent eventListener;
	private final int intervalMs;

	public interface LocationEvent {
		void onLocationChanged(Location location);
	}

	private LocationManager locationManager;
	private AppCompatActivity parent;
	private Location lastLocation;

	public FusedLocationProvider(AppCompatActivity parent, int intervalMs, LocationEvent eventListener) {
		this.parent = parent;
		this.configs = Configs.instance(parent);
		this.eventListener = eventListener;
		this.intervalMs = intervalMs;

		lastLocation = new Location(LocationManager.PASSIVE_PROVIDER);
		lastLocation.setLatitude(configs.getFloat(Configs.ConfigKey.virtualCameraDegLat));
		lastLocation.setLongitude(configs.getFloat(Configs.ConfigKey.virtualCameraDegLon));
		lastLocation.setAccuracy(999);
		lastLocation.setTime(SystemClock.elapsedRealtimeNanos());

		updateListeners();

		this.locationManager = (LocationManager) parent
				.getSystemService(Context.LOCATION_SERVICE);
	}

	public void onResume() {
		if (ActivityCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return;
		}

		for (String providerStr: locationManager.getAllProviders()) {
			locationManager.requestLocationUpdates(providerStr, intervalMs,
					MINIMUM_DISTANCE_METERS, FusedLocationProvider.this);
		}
	}

	public void onPause() {
		locationManager.removeUpdates(this);
	}

	private void updateListeners() {
		eventListener.onLocationChanged(lastLocation);
	}

	@Override
	public void onLocationChanged(@NonNull Location location) {
		long compareTime = SystemClock.elapsedRealtimeNanos();
		float oldLocationAccuracy = lastLocation.getAccuracy() + TimeUnit.NANOSECONDS.toSeconds(compareTime - lastLocation.getElapsedRealtimeNanos());
		float newLocationAccuracy = location.getAccuracy() + TimeUnit.NANOSECONDS.toSeconds(compareTime - location.getElapsedRealtimeNanos());

		if (newLocationAccuracy < oldLocationAccuracy) {
			lastLocation = new Location(location);
		}
		updateListeners();
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {

	}

	@Override
	public void onProviderEnabled(@NonNull String provider) {

	}

	@Override
	public void onProviderDisabled(@NonNull String provider) {

	}
}
