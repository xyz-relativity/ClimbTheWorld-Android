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
 * says. Two or more landmarks far enough apart also tell the heading, which is weighed against
 * the compass by how far apart they are: the farther, the less their own mapping errors turn it.
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
	// About a minute of compass readings, as the editor gets them, and of GPS fixes.
	static final int MAX_HEADING_READINGS = 300;
	static final int MAX_LOCATION_READINGS = 60;
	// A couple of seconds of compass readings, before the offset is trusted.
	static final int MIN_HEADING_READINGS = 10;
	// How far off a mapped node may be from where it really is.
	static final double LANDMARK_ACCURACY_METERS = 2;
	// Landmarks closer together than this, around their middle, tell nothing of the heading.
	static final double MIN_LANDMARK_SPREAD_METERS = 2;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;

	// Per reading: sine and cosine of the offset it tells, and its weight.
	private final ArrayDeque<double[]> headings = new ArrayDeque<>();
	// Per fix: its east and north of the reference, its weight, and the session's x and z of the
	// phone.
	private final ArrayDeque<double[]> locations = new ArrayDeque<>();
	private List<Landmark> landmarks = Collections.emptyList();
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
		referenceSet = false;
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
		if (!landmarks.isEmpty()) {
			setReference(landmarks.get(0).latitude(), landmarks.get(0).longitude());
		}
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
		return headings.size() >= MIN_HEADING_READINGS || landmarkHeading() != null;
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
	 * @return the clockwise angle from the session's north to true north, in degrees, NaN until
	 * there is any compass reading or the landmarks tell it
	 */
	public double getHeadingOffsetDegrees() {
		double[] compass = compassHeading();
		double[] fromLandmarks = landmarkHeading();
		if (compass == null && fromLandmarks == null) {
			return Double.NaN;
		}
		// Each weighted by how far off it may be.
		double sin = 0;
		double cos = 0;
		for (double[] heading : new double[][]{compass, fromLandmarks}) {
			if (heading != null) {
				double weight = 1 / (heading[1] * heading[1]);
				sin += Math.sin(heading[0]) * weight;
				cos += Math.cos(heading[0]) * weight;
			}
		}
		return Math.toDegrees(Math.atan2(sin, cos));
	}

	/**
	 * @return the offset the compass tells and how far off it may be, both in radians, null
	 * without any reading. Averaging does not make it any less off, as a compass near rock is
	 * mostly off the same way for a while.
	 */
	private double[] compassHeading() {
		if (headings.isEmpty()) {
			return null;
		}
		double sin = 0;
		double cos = 0;
		double weights = 0;
		for (double[] heading : headings) {
			sin += heading[0] * heading[2];
			cos += heading[1] * heading[2];
			weights += heading[2];
		}
		return new double[]{Math.atan2(sin, cos),
				Math.toRadians(Math.sqrt(headings.size() / weights))};
	}

	/**
	 * The turn that best puts the landmarks, around their middle, where the map has them.
	 *
	 * @return the offset and how far off it may be, both in radians, null when the landmarks are
	 * too few or too close together to tell
	 */
	private double[] landmarkHeading() {
		if (landmarks.size() < 2) {
			return null;
		}
		double[][] session = new double[landmarks.size()][];
		double[][] map = new double[landmarks.size()][];
		double[] sessionMiddle = new double[2];
		double[] mapMiddle = new double[2];
		for (int i = 0; i < landmarks.size(); i++) {
			Landmark landmark = landmarks.get(i);
			session[i] = new double[]{landmark.sessionX(), -landmark.sessionZ()};
			map[i] = toReference(landmark.latitude(), landmark.longitude());
			for (int axis = 0; axis < 2; axis++) {
				sessionMiddle[axis] += session[i][axis] / landmarks.size();
				mapMiddle[axis] += map[i][axis] / landmarks.size();
			}
		}

		double spread = 0;
		double sin = 0;
		double cos = 0;
		for (int i = 0; i < landmarks.size(); i++) {
			double sessionEast = session[i][0] - sessionMiddle[0];
			double sessionNorth = session[i][1] - sessionMiddle[1];
			double mapEast = map[i][0] - mapMiddle[0];
			double mapNorth = map[i][1] - mapMiddle[1];
			spread += sessionEast * sessionEast + sessionNorth * sessionNorth;
			sin += mapEast * sessionNorth - mapNorth * sessionEast;
			cos += mapEast * sessionEast + mapNorth * sessionNorth;
		}
		if (spread < MIN_LANDMARK_SPREAD_METERS * MIN_LANDMARK_SPREAD_METERS) {
			return null;
		}
		return new double[]{Math.atan2(sin, cos), LANDMARK_ACCURACY_METERS / Math.sqrt(spread)};
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
