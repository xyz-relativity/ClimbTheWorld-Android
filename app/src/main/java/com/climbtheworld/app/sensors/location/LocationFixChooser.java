package com.climbtheworld.app.sensors.location;

import java.util.Objects;

/**
 * Decides which fix to show when several providers (gps, network, fused) report at once.
 * Taking whichever fix was a little more accurate at each update made the position, and most
 * visibly the altitude, flicker between providers that disagree by a few metres. So the provider
 * in use keeps its place until another one is clearly more accurate.
 */
final class LocationFixChooser {
	// An older fix is trusted less: it loses this much accuracy per second of age.
	private static final double ACCURACY_LOSS_METERS_PER_SECOND = 1;
	// Another provider takes over only with a fix at least this much more accurate, so two
	// providers about as accurate as each other do not keep swapping places.
	private static final double SWITCH_ACCURACY_RATIO = 2.0 / 3.0;

	private LocationFixChooser() {
		//hide constructor
	}

	/**
	 * @return true when the new fix should replace the current one: it is the next fix from the
	 * same provider, or another provider's fix that is clearly more accurate once both are aged to
	 * the time of the newer one.
	 */
	static boolean shouldReplace(String currentProvider, float currentAccuracyMeters,
	                             long currentElapsedNanos, String newProvider,
	                             float newAccuracyMeters, long newElapsedNanos) {
		if (Objects.equals(currentProvider, newProvider)) {
			return newElapsedNanos >= currentElapsedNanos;
		}

		long referenceNanos = Math.max(currentElapsedNanos, newElapsedNanos);
		return agedAccuracy(newAccuracyMeters, newElapsedNanos, referenceNanos)
				< agedAccuracy(currentAccuracyMeters, currentElapsedNanos, referenceNanos)
				* SWITCH_ACCURACY_RATIO;
	}

	private static double agedAccuracy(float accuracyMeters, long elapsedNanos,
	                                   long referenceNanos) {
		return accuracyMeters
				+ ACCURACY_LOSS_METERS_PER_SECOND * (referenceNanos - elapsedNanos) / 1e9;
	}
}
