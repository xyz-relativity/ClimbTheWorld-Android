package com.climbtheworld.app.sensors.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.lang.ref.WeakReference;

public class FuseLocationProvider implements LocationListener {
	private static final float MINIMUM_DISTANCE_METERS = 0f;

	private LocationEvent eventListener;
	private final int intervalMs;

	public interface LocationEvent {
		void onLocationChanged(Location location);
	}

	private final LocationManager locationManager;
	private final WeakReference<AppCompatActivity> parent;
	private Location lastLocation;

	public FuseLocationProvider(AppCompatActivity parent, int intervalMs) {
		this.parent = new WeakReference<>(parent);
		this.intervalMs = intervalMs;

		this.locationManager = (LocationManager) this.parent.get().getApplicationContext()
				.getSystemService(Context.LOCATION_SERVICE);
	}

	private boolean hasLocationPermission() {
		return ActivityCompat.checkSelfPermission(parent.get(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(parent.get(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private void initLocation() {
		if (!hasLocationPermission()) {
			return;
		}
		lastLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

		if (lastLocation != null) {
			updateListeners();
		} else {
			lastLocation = new Location(LocationManager.PASSIVE_PROVIDER);
			lastLocation.setAccuracy(999);
		}
	}

	public void requestUpdates(LocationEvent eventListener) {
		this.eventListener = eventListener;
		if (!hasLocationPermission()) {
			return;
		}

		initLocation();

		for (String providerStr: locationManager.getAllProviders()) {
			if (providerStr.equalsIgnoreCase(LocationManager.PASSIVE_PROVIDER)) {
				continue;
			}

			locationManager.requestLocationUpdates(providerStr, intervalMs,
					MINIMUM_DISTANCE_METERS, this);
		}
	}

	public void stopUpdates() {
		locationManager.removeUpdates(this);
		this.eventListener = null;
	}

	private void updateListeners() {
		if (eventListener != null) {
			eventListener.onLocationChanged(lastLocation);
		}
	}

	@Override
	public void onLocationChanged(@NonNull Location location) {
		if (LocationFixChooser.shouldReplace(lastLocation.getProvider(), lastLocation.getAccuracy(),
				lastLocation.getElapsedRealtimeNanos(), location.getProvider(),
				location.getAccuracy(), location.getElapsedRealtimeNanos())) {
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
