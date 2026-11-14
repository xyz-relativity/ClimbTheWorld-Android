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
	// The system's last fix is used on resume only when it is at most this old. It can be hours
	// old and far away (the phone was in a bag on the drive to the crag), and the views would take
	// it as current. A climber on foot is within a couple of hundred metres of a fix this old,
	// which the ageing above already allows for, and a fresh fix normally comes within seconds.
	static final long LAST_KNOWN_FIX_MAX_AGE_NANOS = 2 * 60 * 1_000_000_000L;

	private LocationFixChooser() {
		//hide constructor
	}

	/**
	 * @return true when a fix taken at fixElapsedNanos is at most maxAgeNanos old at
	 * nowElapsedNanos, both on the elapsed realtime clock. A fix a little ahead of now, from clock
	 * skew between the provider and the app, counts as fresh. A fix with no time (0) is too old:
	 * its age is unknown.
	 */
	static boolean isRecentEnough(long fixElapsedNanos, long nowElapsedNanos, long maxAgeNanos) {
		if (fixElapsedNanos <= 0) {
			return false;
		}
		return nowElapsedNanos - fixElapsedNanos <= maxAgeNanos;
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
