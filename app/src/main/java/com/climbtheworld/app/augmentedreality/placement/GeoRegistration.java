package com.climbtheworld.app.augmentedreality.placement;

import com.climbtheworld.app.utils.GeoUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ties an A.R. session's world to the map, from compass and GPS readings, and from mapped nodes
 * the user aimed at.
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
 * Landmarks, mapped nodes the user aimed at, take over from GPS: the session's world is moved to
 * put them where the map has them, so that what is placed lands right next to them, whatever GPS
 * says. With a landmark, the compass's offset is held as it was when the landmark came, or when
 * the compass became ready, rather than followed: following it would slide the phone around the
 * landmark as the field bends, and the session barely drifts. Two or more landmarks far enough
 * apart also tell the heading, and it takes over from the compass once it is the surer of the
 * two: next to rock the compass is steady but off, and the landmarks are what the user wants
 * things lined up with. The farther apart they are, the less their own mapping errors turn the
 * heading, see landmarkHeadingAccuracyDegrees.
 * <p>
 * Positions are worked out on a plane tangent to the Earth at the first fix or landmark, which is
 * exact enough over the few hundred meters a session covers.
 */
public class GeoRegistration {
	// Fixes less accurate than this are left out.
	static final double MAX_LOCATION_ACCURACY_METERS = 50;
	// Readings count as at most this accurate, so that one can't outweigh all the others.
	static final double MIN_LOCATION_ACCURACY_METERS = 3;
	static final double MIN_HEADING_ACCURACY_DEGREES = 3;
	// How accurate a compass reading counts as when the device does not tell.
	static final double DEFAULT_HEADING_ACCURACY_DEGREES = 15;
	// Against the landmarks' heading, the compass counts as no surer than this, however sure the
	// device says it is: it does not know of the rock bending the field. Landmarks about 16 m
	// apart are then the surer.
	public static final double MAX_COMPARED_COMPASS_ACCURACY_DEGREES = 10;
	// Compass readings come at the sensors' rate, dozens a second: only one every this long is
	// kept, so that the window of readings spans a walk rather than a glance, and the bends of
	// the field at one spot are averaged against those at others.
	static final double MIN_HEADING_INTERVAL_SECONDS = 0.2;
	// About a minute of compass readings, see MIN_HEADING_INTERVAL_SECONDS, and of GPS fixes.
	static final int MAX_HEADING_READINGS = 300;
	static final int MAX_LOCATION_READINGS = 60;
	// A couple of seconds of compass readings, before the offset is trusted. With a landmark, one
	// is enough: the reading is already steadied against the gyroscope, and the landmark holds
	// it, see holdCompass.
	static final int MIN_HEADING_READINGS = 10;
	// How far off a mapped node may be from where it really is.
	static final double LANDMARK_ACCURACY_METERS = 2;
	// Two landmarks closer together than this tell nothing of the heading: aiming a little off
	// would turn it a lot.
	static final double MIN_LANDMARK_SPREAD_METERS = 3;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;

	// Per reading: sine and cosine of the offset it tells, and its weight.
	private final ArrayDeque<double[]> headings = new ArrayDeque<>();
	// When the latest compass reading kept was taken, see MIN_HEADING_INTERVAL_SECONDS.
	private long lastHeadingTimestampNanos;
	// Per fix: its east and north of the reference, its weight, and the session's x and z of the
	// phone.
	private final ArrayDeque<double[]> locations = new ArrayDeque<>();
	private List<Landmark> landmarks = Collections.emptyList();
	// The compass's offset, in radians, and its accuracy, in degrees, held while there are
	// landmarks; NaN while following the compass.
	private double heldCompassOffset = Double.NaN;
	private double heldCompassAccuracyDegrees;
	private boolean referenceSet;
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
	 * Forgets every reading and landmark, for a session whose world is new.
	 */
	public void reset() {
		headings.clear();
		locations.clear();
		landmarks = Collections.emptyList();
		heldCompassOffset = Double.NaN;
		referenceSet = false;
	}

	/**
	 * @param compassAzimuthDegrees the camera's azimuth from the compass, from true north
	 * @param sessionAzimuthDegrees the camera's azimuth in the session's world, see
	 *                              sessionAzimuthDegrees
	 * @param accuracyDegrees       how far off the compass may be, NaN when unknown
	 * @param timestampNanos        when the reading was taken, on any steady clock: readings
	 *                              closer than MIN_HEADING_INTERVAL_SECONDS to the last one
	 *                              kept are left out
	 */
	public void addHeading(double compassAzimuthDegrees, double sessionAzimuthDegrees,
	                       double accuracyDegrees, long timestampNanos) {
		if (!headings.isEmpty()
				&& timestampNanos - lastHeadingTimestampNanos < MIN_HEADING_INTERVAL_SECONDS * 1e9) {
			return;
		}
		lastHeadingTimestampNanos = timestampNanos;
		double accuracy = Double.isNaN(accuracyDegrees) ? DEFAULT_HEADING_ACCURACY_DEGREES
				: Math.max(accuracyDegrees, MIN_HEADING_ACCURACY_DEGREES);
		double offset = Math.toRadians(compassAzimuthDegrees - sessionAzimuthDegrees);
		add(headings, new double[]{Math.sin(offset), Math.cos(offset), 1 / (accuracy * accuracy)},
				MAX_HEADING_READINGS);
		holdCompass();
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
		setReference(latitude, longitude);
		double accuracy = Math.max(accuracyMeters, MIN_LOCATION_ACCURACY_METERS);
		double[] eastNorth = toReference(latitude, longitude);
		add(locations, new double[]{eastNorth[0], eastNorth[1], 1 / (accuracy * accuracy),
				sessionX, sessionZ}, MAX_LOCATION_READINGS);
	}

	/**
	 * Replaces the landmarks, as the session has them now: it keeps learning where they are.
	 */
	public void setLandmarks(List<Landmark> landmarks) {
		this.landmarks = new ArrayList<>(landmarks);
		if (landmarks.isEmpty()) {
			heldCompassOffset = Double.NaN;
			return;
		}
		setReference(landmarks.get(0).latitude(), landmarks.get(0).longitude());
		holdCompass();
	}

	/**
	 * Holds the compass's offset as it is now, with landmarks and once the compass is ready,
	 * unless it is held already.
	 */
	private void holdCompass() {
		if (landmarks.isEmpty() || !Double.isNaN(heldCompassOffset) || headings.isEmpty()) {
			return;
		}
		heldCompassOffset = averageCompassHeading();
		heldCompassAccuracyDegrees = averageCompassAccuracyDegrees();
	}

	private void setReference(double latitude, double longitude) {
		if (referenceSet) {
			return;
		}
		referenceSet = true;
		referenceLatitude = latitude;
		referenceLongitude = longitude;
		metersPerDegreeLongitude = METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude));
	}

	private double[] toReference(double latitude, double longitude) {
		return new double[]{(longitude - referenceLongitude) * metersPerDegreeLongitude,
				(latitude - referenceLatitude) * METERS_PER_DEGREE};
	}

	/**
	 * @return whether which way north is in the session's world is known
	 */
	public boolean hasHeading() {
		return headings.size() >= (landmarks.isEmpty() ? MIN_HEADING_READINGS : 1)
				|| !Double.isNaN(landmarkHeading());
	}

	/**
	 * @return whether where the session's world is on Earth is known
	 */
	public boolean hasLocation() {
		return !locations.isEmpty() || !landmarks.isEmpty();
	}

	public boolean isReady() {
		return hasHeading() && hasLocation();
	}

	/**
	 * @return the clockwise angle from the session's north to true north, in degrees: as the
	 * landmarks tell it when they can and are surer than the compass, see
	 * MAX_COMPARED_COMPASS_ACCURACY_DEGREES, else as the compass does, NaN until there is any
	 * compass reading
	 */
	public double getHeadingOffsetDegrees() {
		return Math.toDegrees(landmarksSetHeading() ? landmarkHeading() : compassHeading());
	}

	/**
	 * @return whether the landmarks set the heading rather than the compass, see
	 * getHeadingOffsetDegrees
	 */
	public boolean landmarksSetHeading() {
		if (Double.isNaN(landmarkHeading())) {
			return false;
		}
		double compassAccuracy = Math.max(compassAccuracyDegrees(),
				MAX_COMPARED_COMPASS_ACCURACY_DEGREES);
		return Double.isNaN(compassAccuracy)
				|| landmarkHeadingAccuracyDegrees(landmarkEastNorth()) <= compassAccuracy;
	}

	/**
	 * @return how far off getHeadingOffsetDegrees may be, in degrees: as the landmarks' spread
	 * tells when they set it, see landmarkHeadingAccuracyDegrees, else as the compass's readings
	 * do, see compassAccuracyDegrees; NaN without any reading
	 */
	public double headingAccuracyDegrees() {
		return landmarksSetHeading() ? landmarkHeadingAccuracyDegrees(landmarkEastNorth())
				: compassAccuracyDegrees();
	}

	/**
	 * @return the offset the compass tells, in radians, held while there are landmarks, NaN
	 * without any reading
	 */
	private double compassHeading() {
		return Double.isNaN(heldCompassOffset) ? averageCompassHeading() : heldCompassOffset;
	}

	/**
	 * @return how far off the offset the compass tells may be, in degrees, held while there are
	 * landmarks, NaN without any reading
	 */
	public double compassAccuracyDegrees() {
		return Double.isNaN(heldCompassOffset) ? averageCompassAccuracyDegrees()
				: heldCompassAccuracyDegrees;
	}

	/**
	 * @return the offset the latest readings tell, in radians, NaN without any reading.
	 * Averaging does not make it any less off, as a compass near rock is mostly off the same way
	 * for a while.
	 */
	private double averageCompassHeading() {
		if (headings.isEmpty()) {
			return Double.NaN;
		}
		double sin = 0;
		double cos = 0;
		for (double[] heading : headings) {
			sin += heading[0] * heading[2];
			cos += heading[1] * heading[2];
		}
		return Math.atan2(sin, cos);
	}

	/**
	 * @return the accuracy of the latest readings taken together, in degrees: that of a reading
	 * of the mean weight, as averaging a compass that is off the same way for a while does not
	 * make it surer; NaN without any reading
	 */
	private double averageCompassAccuracyDegrees() {
		if (headings.isEmpty()) {
			return Double.NaN;
		}
		double weights = 0;
		for (double[] heading : headings) {
			weights += heading[2];
		}
		return Math.sqrt(headings.size() / weights);
	}

	/**
	 * How far landmarks at some spots are spread around their middle: the sum of their squared
	 * distances from it, in square meters, so for two landmarks half the square of how far apart
	 * they are.
	 *
	 * @param eastNorth where each landmark is, in meters east and north of any one point
	 */
	private static double spread(List<double[]> eastNorth) {
		double[] middle = new double[2];
		for (double[] point : eastNorth) {
			for (int axis = 0; axis < 2; axis++) {
				middle[axis] += point[axis] / eastNorth.size();
			}
		}
		double spread = 0;
		for (double[] point : eastNorth) {
			double east = point[0] - middle[0];
			double north = point[1] - middle[1];
			spread += east * east + north * north;
		}
		return spread;
	}

	/**
	 * @return whether landmarks at some spots are far enough apart to tell the heading, see
	 * MIN_LANDMARK_SPREAD_METERS
	 */
	private static boolean tellsHeading(List<double[]> eastNorth) {
		return eastNorth.size() >= 2
				&& spread(eastNorth) >= MIN_LANDMARK_SPREAD_METERS * MIN_LANDMARK_SPREAD_METERS / 2;
	}

	/**
	 * How far off the heading landmarks at some spots tell may be, as each may be mapped
	 * LANDMARK_ACCURACY_METERS off: the farther apart they are, the less that turns it.
	 *
	 * @param eastNorth where each landmark is, in meters east and north of any one point, as
	 *                  the map has them
	 * @return in degrees, NaN when they are too few or too close together to tell a heading
	 */
	public static double landmarkHeadingAccuracyDegrees(List<double[]> eastNorth) {
		if (!tellsHeading(eastNorth)) {
			return Double.NaN;
		}
		return Math.toDegrees(LANDMARK_ACCURACY_METERS / Math.sqrt(spread(eastNorth)));
	}

	/**
	 * The turn that best puts the landmarks, around their middle, where the map has them.
	 *
	 * @return the offset in radians, NaN when the landmarks are too few or too close together to
	 * tell
	 */
	private double landmarkHeading() {
		List<double[]> session = new ArrayList<>(landmarks.size());
		for (Landmark landmark : landmarks) {
			session.add(new double[]{landmark.sessionX(), -landmark.sessionZ()});
		}
		List<double[]> map = landmarkEastNorth();
		if (!tellsHeading(session)) {
			return Double.NaN;
		}
		double[] sessionMiddle = new double[2];
		double[] mapMiddle = new double[2];
		for (int i = 0; i < landmarks.size(); i++) {
			for (int axis = 0; axis < 2; axis++) {
				sessionMiddle[axis] += session.get(i)[axis] / landmarks.size();
				mapMiddle[axis] += map.get(i)[axis] / landmarks.size();
			}
		}
		double sin = 0;
		double cos = 0;
		for (int i = 0; i < landmarks.size(); i++) {
			double sessionEast = session.get(i)[0] - sessionMiddle[0];
			double sessionNorth = session.get(i)[1] - sessionMiddle[1];
			double mapEast = map.get(i)[0] - mapMiddle[0];
			double mapNorth = map.get(i)[1] - mapMiddle[1];
			sin += mapEast * sessionNorth - mapNorth * sessionEast;
			cos += mapEast * sessionEast + mapNorth * sessionNorth;
		}
		return Math.atan2(sin, cos);
	}

	/**
	 * Where the map has the landmarks, in meters east and north of the reference.
	 */
	private List<double[]> landmarkEastNorth() {
		List<double[]> map = new ArrayList<>(landmarks.size());
		for (Landmark landmark : landmarks) {
			map.add(toReference(landmark.latitude(), landmark.longitude()));
		}
		return map;
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

		// Each landmark, or else each fix, tells where the session's origin is: where it is on
		// the map, less where the session has it.
		double originEast = 0;
		double originNorth = 0;
		double weights = 0;
		if (!landmarks.isEmpty()) {
			for (Landmark landmark : landmarks) {
				double[] mapped = toReference(landmark.latitude(), landmark.longitude());
				double[] seen = toEastNorth(landmark.sessionX(), landmark.sessionZ(), sin, cos);
				originEast += mapped[0] - seen[0];
				originNorth += mapped[1] - seen[1];
				weights++;
			}
		} else {
			for (double[] location : locations) {
				double[] phone = toEastNorth(location[3], location[4], sin, cos);
				originEast += (location[0] - phone[0]) * location[2];
				originNorth += (location[1] - phone[1]) * location[2];
				weights += location[2];
			}
		}
		originEast /= weights;
		originNorth /= weights;

		double[] point = toEastNorth(sessionX, sessionZ, sin, cos);
		return new double[]{referenceLatitude + (originNorth + point[1]) / METERS_PER_DEGREE,
				referenceLongitude + (originEast + point[0]) / metersPerDegreeLongitude};
	}

	/**
	 * A mapped node the user aimed at: where the session has it, and where the map has it.
	 */
	public record Landmark(double sessionX, double sessionZ, double latitude, double longitude) {
	}
}
