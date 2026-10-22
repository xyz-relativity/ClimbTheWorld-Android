package com.climbtheworld.app.augmentedreality.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Which routes to suggest aligning on next, once a route is aligned on: the heading comes from
 * how far apart the routes aligned on are, see GeoRegistration.landmarkHeadingAccuracyDegrees, so
 * the next route is best far from them, yet near enough to the user to aim at. Until a route is
 * aligned on, nothing is suggested: the closest is as good as any. And when the routes aligned
 * on are enough, see isHeadingGood, the user is no longer prompted to align on more.
 */
public final class AlignmentSuggestions {
	// How far from the user a route can be aimed at: ARCore measures depth up to about 20 m
	// outdoors, a little more as the user aims from where they stand.
	public static final double MAX_AIM_METERS = 25;
	// A heading no surer than this is not worth suggesting a route for: it is about what the
	// compass is worth.
	public static final double WORST_SUGGESTED_DEGREES =
			GeoRegistration.DEFAULT_HEADING_ACCURACY_DEGREES;
	// A heading this sure is good enough to stop prompting the user to align on more routes: two
	// routes about 16 m apart, as they may be mapped a couple of meters off.
	public static final double GOOD_HEADING_DEGREES = 10;
	public static final int MAX_SUGGESTED = 3;
	private static final double METERS_PER_DEGREE = 6378137 * Math.PI / 180;

	private AlignmentSuggestions() {
	}

	/**
	 * Where a spot is from another, in meters east and north of it.
	 */
	public static double[] eastNorthMeters(double latitude, double longitude,
	                                       double fromLatitude, double fromLongitude) {
		double north = (latitude - fromLatitude) * METERS_PER_DEGREE;
		double east = (longitude - fromLongitude) * METERS_PER_DEGREE
				* Math.cos(Math.toRadians(fromLatitude));
		return new double[]{east, north};
	}

	/**
	 * @param alignedEastNorth where the routes aligned on are, in meters east and north of any
	 *                         one point
	 * @return whether they set the heading to GOOD_HEADING_DEGREES or better
	 */
	public static boolean isHeadingGood(List<double[]> alignedEastNorth) {
		return GeoRegistration.landmarkHeadingAccuracyDegrees(alignedEastNorth)
				<= GOOD_HEADING_DEGREES;
	}

	/**
	 * @param candidates       the routes around, the ones aligned on left out
	 * @param eastNorth        where a route is, in meters east and north of the user
	 * @param alignedEastNorth where the routes aligned on are, the same way
	 * @return up to MAX_SUGGESTED routes, the one giving the surest heading first; none until a
	 * route is aligned on, or while no route near enough gives a heading worth it
	 */
	public static <T> List<T> suggested(List<T> candidates, Function<T, double[]> eastNorth,
	                                    List<double[]> alignedEastNorth) {
		if (alignedEastNorth.isEmpty()) {
			return Collections.emptyList();
		}
		List<Scored<T>> scored = new ArrayList<>();
		for (T candidate : candidates) {
			double[] at = eastNorth.apply(candidate);
			if (Math.hypot(at[0], at[1]) > MAX_AIM_METERS) {
				continue;
			}
			List<double[]> landmarks = new ArrayList<>(alignedEastNorth);
			landmarks.add(at);
			double accuracy = GeoRegistration.landmarkHeadingAccuracyDegrees(landmarks);
			if (accuracy <= WORST_SUGGESTED_DEGREES) {
				scored.add(new Scored<>(candidate, accuracy));
			}
		}
		scored.sort(Comparator.comparingDouble(Scored::accuracyDegrees));
		List<T> suggested = new ArrayList<>();
		for (Scored<T> entry : scored.subList(0, Math.min(MAX_SUGGESTED, scored.size()))) {
			suggested.add(entry.route());
		}
		return suggested;
	}

	private record Scored<T>(T route, double accuracyDegrees) {
	}
}
