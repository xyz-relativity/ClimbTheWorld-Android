package com.climbtheworld.app.sensors.location;

import android.content.Context;
import android.location.Location;
import android.location.altitude.AltitudeConverter;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.climbtheworld.app.utils.constants.Constants;

import java.io.IOException;

import needle.UiRelatedTask;

/**
 * GPS altitudes are heights above the WGS84 ellipsoid, while OSM elevations are heights above sea
 * level (the geoid). The two differ by tens of metres in most places, so altitudes are converted
 * before being compared with node elevations or written to nodes.
 */
public final class SeaLevelAltitude {
	// The geoid height changes by a few metres at most over this distance, usually much less.
	private static final double GEOID_UPDATE_DEGREES = 0.05;

	// Difference between sea level and ellipsoid heights around the location it was computed for.
	// Only touched from the main thread.
	private static double geoidOffsetLatitude = Double.NaN;
	private static double geoidOffsetLongitude = Double.NaN;
	private static double geoidOffset;
	private static boolean computingGeoidOffset;
	private static boolean conversionUnavailable;

	private SeaLevelAltitude() {
		//hide constructor
	}

	/**
	 * @return the altitude above sea level in meters, or 0 when it is unknown: the location has no
	 * altitude, the device cannot convert it (before Android 14) or the conversion is still
	 * loading.
	 */
	public static double getAltitude(Context context, Location location) {
		if (location.hasAltitude() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			return getConvertedAltitude(context, location);
		}
		return 0;
	}

	@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
	private static double getConvertedAltitude(Context context, Location location) {
		if (location.hasMslAltitude()) {
			return location.getMslAltitudeMeters();
		}

		if (!Double.isNaN(geoidOffsetLatitude)
				&& Math.abs(location.getLatitude() - geoidOffsetLatitude) <= GEOID_UPDATE_DEGREES
				&& Math.abs(location.getLongitude() - geoidOffsetLongitude) <= GEOID_UPDATE_DEGREES) {
			return location.getAltitude() + geoidOffset;
		}

		computeGeoidOffset(context.getApplicationContext(), new Location(location));
		return 0;
	}

	@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
	private static void computeGeoidOffset(Context context, Location location) {
		if (computingGeoidOffset || conversionUnavailable) {
			return;
		}
		computingGeoidOffset = true;

		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Double>() {
			@Override
			protected Double doWork() {
				try {
					// Reads the geoid model from disk, so it has to stay off the main thread.
					new AltitudeConverter().addMslAltitudeToLocation(context, location);
					return location.getMslAltitudeMeters() - location.getAltitude();
				} catch (IOException | IllegalArgumentException e) {
					return null;
				}
			}

			@Override
			protected void thenDoUiRelatedWork(Double offset) {
				computingGeoidOffset = false;
				if (offset == null) {
					// Retrying on every location update would not help.
					conversionUnavailable = true;
					return;
				}
				geoidOffset = offset;
				geoidOffsetLatitude = location.getLatitude();
				geoidOffsetLongitude = location.getLongitude();
			}
		});
	}
}
