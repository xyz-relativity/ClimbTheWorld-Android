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
		Location lastKnown = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

		// A last fix too old to be where the phone is now would be taken as current by the views:
		// it is left out, and the screen keeps its saved position until a fresh fix comes.
		if (lastKnown != null && LocationFixChooser.isRecentEnough(
				lastKnown.getElapsedRealtimeNanos(), SystemClock.elapsedRealtimeNanos(),
				LocationFixChooser.LAST_KNOWN_FIX_MAX_AGE_NANOS)) {
			// The fix in use is kept across a pause, and the system's last fix can be older or
			// less accurate than it, so it has to win like any other fix.
			onLocationChanged(lastKnown);
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
		// The first fix is taken whatever it is: there is nothing to compare it with.
		if (lastLocation == null || LocationFixChooser.shouldReplace(lastLocation.getProvider(),
				lastLocation.getAccuracy(), lastLocation.getElapsedRealtimeNanos(),
				location.getProvider(), location.getAccuracy(),
				location.getElapsedRealtimeNanos())) {
			lastLocation = new Location(location);
		}
		// Told on every provider's report, as the screens have always been, also when the report
		// is turned down and the fix in use is told again. The A.R. alignment counts a fix told
		// again once, see GeoRegistration.addLocation.
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
