package com.climbtheworld.app.augmentedreality.placement;

import com.climbtheworld.app.utils.GeoUtils;

import java.util.ArrayDeque;

/**
 * Ties an A.R. session's world to the map, from compass and GPS readings.
 * <p>
 * The session's world only knows where the phone went since the session started: +Y is up, but
 * its horizontal axes point any way. Here its -Z axis is taken as the session's north and +X as
 * its east, and the heading offset is the clockwise angle from the session's north to true
 * north. Each compass reading of the camera's azimuth, against the session's own azimuth of the
 * camera, tells the offset; each GPS fix, with where the session had the phone then, tells where
 * the session's origin is on Earth. As the session barely drifts, both are averaged over the
 * latest readings, weighted by how accurate the device says they are, which makes them steadier
 * than any single reading.
 * <p>
 * Positions are worked out on a plane tangent to the Earth at the first fix, which is exact
 * enough over the few hundred meters a session covers.
 */
public class GeoRegistration {
	// Fixes less accurate than this are left out.
	static final double MAX_LOCATION_ACCURACY_METERS = 50;
	// Readings count as at most this accurate, so that one can't outweigh all the others.
	static final double MIN_LOCATION_ACCURACY_METERS = 3;
	static final double MIN_HEADING_ACCURACY_DEGREES = 3;
	// How accurate a compass reading counts as when the device does not tell.
	static final double DEFAULT_HEADING_ACCURACY_DEGREES = 15;
	// About a minute of compass readings, as the editor gets them, and of GPS fixes.
	static final int MAX_HEADING_READINGS = 300;
	static final int MAX_LOCATION_READINGS = 60;
	// A couple of seconds of compass readings, before the offset is trusted.
	static final int MIN_HEADING_READINGS = 10;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;

	// Per reading: sine and cosine of the offset it tells, and its weight.
	private final ArrayDeque<double[]> headings = new ArrayDeque<>();
	// Per fix: its east and north of the reference, its weight, and the session's x and z of the
	// phone.
	private final ArrayDeque<double[]> locations = new ArrayDeque<>();
	private double referenceLatitude;
	private double referenceLongitude;
	private double metersPerDegreeLongitude;

	/**
	 * Azimuth of a horizontal direction of the session's world, in degrees clockwise from the
	 * session's north.
	 */
	public static double sessionAzimuthDegrees(double x, double z) {
		return Math.toDegrees(Math.atan2(x, -z));
	}

	private static <T> void add(ArrayDeque<T> readings, T reading, int max) {
		readings.addLast(reading);
		if (readings.size() > max) {
			readings.removeFirst();
		}
	}

	/**
	 * The east and north offset of a horizontal offset of the session's world.
	 */
	private static double[] toEastNorth(double sessionX, double sessionZ, double sin, double cos) {
		double east = sessionX;
		double north = -sessionZ;
		return new double[]{east * cos + north * sin, north * cos - east * sin};
	}

	/**
	 * Forgets every reading, for a session whose world is new.
	 */
	public void reset() {
		headings.clear();
		locations.clear();
	}

	/**
	 * @param compassAzimuthDegrees the camera's azimuth from the compass, from true north
	 * @param sessionAzimuthDegrees the camera's azimuth in the session's world, see
	 *                              sessionAzimuthDegrees
	 * @param accuracyDegrees       how far off the compass may be, NaN when unknown
	 */
	public void addHeading(double compassAzimuthDegrees, double sessionAzimuthDegrees,
	                       double accuracyDegrees) {
		double accuracy = Double.isNaN(accuracyDegrees) ? DEFAULT_HEADING_ACCURACY_DEGREES
				: Math.max(accuracyDegrees, MIN_HEADING_ACCURACY_DEGREES);
		double offset = Math.toRadians(compassAzimuthDegrees - sessionAzimuthDegrees);
		add(headings, new double[]{Math.sin(offset), Math.cos(offset), 1 / (accuracy * accuracy)},
				MAX_HEADING_READINGS);
	}

	/**
	 * @param accuracyMeters how far off the fix may be
	 * @param sessionX       where the session had the phone at the time of the fix
	 * @param sessionZ       where the session had the phone at the time of the fix
	 */
	public void addLocation(double latitude, double longitude, double accuracyMeters,
	                        double sessionX, double sessionZ) {
		if (!(accuracyMeters <= MAX_LOCATION_ACCURACY_METERS)) {
			return;
		}
		if (locations.isEmpty()) {
			referenceLatitude = latitude;
			referenceLongitude = longitude;
			metersPerDegreeLongitude = METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude));
		}
		double accuracy = Math.max(accuracyMeters, MIN_LOCATION_ACCURACY_METERS);
		add(locations, new double[]{(longitude - referenceLongitude) * metersPerDegreeLongitude,
						(latitude - referenceLatitude) * METERS_PER_DEGREE, 1 / (accuracy * accuracy),
						sessionX, sessionZ},
				MAX_LOCATION_READINGS);
	}

	public boolean hasHeading() {
		return headings.size() >= MIN_HEADING_READINGS;
	}

	public boolean hasLocation() {
		return !locations.isEmpty();
	}

	public boolean isReady() {
		return hasHeading() && hasLocation();
	}

	/**
	 * @return the clockwise angle from the session's north to true north, in degrees, NaN without
	 * any compass reading
	 */
	public double getHeadingOffsetDegrees() {
		if (headings.isEmpty()) {
			return Double.NaN;
		}
		double sin = 0;
		double cos = 0;
		for (double[] heading : headings) {
			sin += heading[0] * heading[2];
			cos += heading[1] * heading[2];
		}
		return Math.toDegrees(Math.atan2(sin, cos));
	}

	/**
	 * Where a point of the session's world is on Earth.
	 *
	 * @return its latitude and longitude, null until isReady
	 */
	public double[] toLatLon(double sessionX, double sessionZ) {
		if (!isReady()) {
			return null;
		}
		double offset = Math.toRadians(getHeadingOffsetDegrees());
		double sin = Math.sin(offset);
		double cos = Math.cos(offset);

		// Each fix tells where the session's origin is: where the fix was, less where the session
		// had the phone.
		double originEast = 0;
		double originNorth = 0;
		double weights = 0;
		for (double[] location : locations) {
			double[] phone = toEastNorth(location[3], location[4], sin, cos);
			originEast += (location[0] - phone[0]) * location[2];
			originNorth += (location[1] - phone[1]) * location[2];
			weights += location[2];
		}
		originEast /= weights;
		originNorth /= weights;

		double[] point = toEastNorth(sessionX, sessionZ, sin, cos);
		return new double[]{referenceLatitude + (originNorth + point[1]) / METERS_PER_DEGREE,
				referenceLongitude + (originEast + point[0]) / metersPerDegreeLongitude};
	}
}
