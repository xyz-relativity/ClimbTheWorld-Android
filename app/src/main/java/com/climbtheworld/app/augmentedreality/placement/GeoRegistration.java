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
 * latest readings, weighted by how accurate the device says they are, and the fixes also by how
 * recent they are, which makes them steadier than any single reading.
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
	// About a minute of compass readings, see MIN_HEADING_INTERVAL_SECONDS.
	static final int MAX_HEADING_READINGS = 300;
	// A fix weighs by how long it was the one in use, each second of it weighing half as much
	// for every this long ago it was. The phone is put at the average of the fixes, moved on by
	// how far the session went since, turned by the heading, so a heading that is off puts it
	// off to the side by that angle times how far back the average reaches along the way walked.
	// A plain average of the last 20 s of fixes has served well: its fixes are on average about
	// 10 s old, and about 20 of them steady it against GPS noise. At one fix a second, halving
	// every 7 s gives the same: a mean age of 1 / (2^(1/7) - 1), about 9.6 s, and
	// (1 + r) / (1 - r) with r = 2^(-1/7), about 20, fixes' worth of steadiness. A fix held for
	// long weighs at most 7 s / ln 2, about ten of those fixes: a new one after it gets to half
	// in about ten seconds, as it did in the plain average, rather than at once.
	static final double LOCATION_HALF_LIFE_SECONDS = 7;
	// How long a new fix counts as in use to start with: the usual time between fixes, so that
	// fixes coming at that pace each weigh as one.
	static final double FIX_INTERVAL_SECONDS = 1;
	// The providers report at least once a second while the A.R. runs: a longer time between
	// reports is a pause, of which only this much counts as the fix in use being seen.
	static final double MAX_REPORT_GAP_SECONDS = 2;
	// About a minute of fixes at one a second, over eight half-lives, see
	// LOCATION_HALF_LIFE_SECONDS: those dropped weigh next to nothing.
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
	// What the readings kept tell together, worked out as one is kept rather than each time it
	// is asked, which is several times for every frame of the camera: the offset, in radians,
	// see averageCompassHeading, and its accuracy, in degrees, see
	// averageCompassAccuracyDegrees; NaN without any reading.
	private double averageCompassOffset = Double.NaN;
	private double averageCompassAccuracy = Double.NaN;
	// Per fix: its east and north of the reference, its weight, the session's x and z of the
	// phone when it came, and the weight of its accuracy. Its weight is that of its accuracy
	// times how long it was in use, in seconds each halved for every LOCATION_HALF_LIFE_SECONDS
	// since, see addLocation, worked out as the fixes are told rather than for every frame of
	// the camera.
	private final ArrayDeque<double[]> locations = new ArrayDeque<>();
	// The newest fix kept, as it was told, and how long it has been in use, in seconds, as far
	// as the reports tell, see MAX_REPORT_GAP_SECONDS.
	private double lastLatitude;
	private double lastLongitude;
	private double lastAccuracyMeters;
	private double newestInUseSeconds;
	// When the latest fix was told, see addLocation.
	private long lastReportTimestampNanos;
	private List<Landmark> landmarks = Collections.emptyList();
	// What the landmarks tell of the heading, worked out as they are set, likewise: the offset,
	// in radians, see landmarkHeading, and how far off it may be, in degrees, see
	// landmarkHeadingAccuracyDegrees; NaN when they tell none.
	private double landmarkOffset = Double.NaN;
	private double landmarkAccuracyDegrees = Double.NaN;
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
		averageCompassOffset = Double.NaN;
		averageCompassAccuracy = Double.NaN;
		locations.clear();
		landmarks = Collections.emptyList();
		landmarkOffset = Double.NaN;
		landmarkAccuracyDegrees = Double.NaN;
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
		averageCompassOffset = averageCompassHeading();
		averageCompassAccuracy = averageCompassAccuracyDegrees();
		holdCompass();
	}

	/**
	 * Takes a fix, against where the session has the phone. The fix in use is told on every
	 * report of the device's location providers, and a GPS may hold the same fix for minutes
	 * while the phone stands still. One just like the newest kept is that fix still in use: it
	 * is not kept again, so that it stays paired with where the phone was when it came, but
	 * the time it was in use adds to its weight, see LOCATION_HALF_LIFE_SECONDS. That time is
	 * the time between reports, whatever their number, so a fix weighs the same however often
	 * it is told. Its first FIX_INTERVAL_SECONDS count from the moment it is kept, as for a fix
	 * that does not stay.
	 *
	 * @param accuracyMeters how far off the fix may be
	 * @param sessionX       where the session had the phone at the time of the fix
	 * @param sessionZ       where the session had the phone at the time of the fix
	 * @param timestampNanos when the fix was told, on any steady clock: going back counts as no
	 *                       time passed
	 */
	public void addLocation(double latitude, double longitude, double accuracyMeters,
	                        double sessionX, double sessionZ, long timestampNanos) {
		if (!(accuracyMeters <= MAX_LOCATION_ACCURACY_METERS)) {
			return;
		}
		boolean first = locations.isEmpty();
		boolean toldAgain = !first && latitude == lastLatitude && longitude == lastLongitude
				&& accuracyMeters == lastAccuracyMeters;
		if (!first) {
			double gapSeconds = Math.max(0, timestampNanos - lastReportTimestampNanos) / 1e9;
			double decay = Math.pow(0.5, gapSeconds / LOCATION_HALF_LIFE_SECONDS);
			for (double[] location : locations) {
				location[2] *= decay;
			}
			// The newest was in use until now, also when a new fix takes over from it, but of a
			// pause only its start counts as seen, and that long ago.
			double seenSeconds = Math.min(gapSeconds, MAX_REPORT_GAP_SECONDS);
			double addedSeconds = Math.max(0, newestInUseSeconds + seenSeconds
					- FIX_INTERVAL_SECONDS) - Math.max(0, newestInUseSeconds - FIX_INTERVAL_SECONDS);
			newestInUseSeconds += seenSeconds;
			double[] newest = locations.getLast();
			if (addedSeconds > 0) {
				double gain = LOCATION_HALF_LIFE_SECONDS / Math.log(2)
						* (1 - Math.pow(0.5, addedSeconds / LOCATION_HALF_LIFE_SECONDS));
				if (gapSeconds > seenSeconds) {
					gain *= Math.pow(0.5, (gapSeconds - seenSeconds) / LOCATION_HALF_LIFE_SECONDS);
				}
				newest[2] += newest[5] * gain;
			}
			if (toldAgain && gapSeconds > MAX_REPORT_GAP_SECONDS) {
				// Seen again after a pause: it leads as a new fix would, over those that faded.
				newest[2] = Math.max(newest[2], newest[5] * FIX_INTERVAL_SECONDS);
			}
		}
		if (first || timestampNanos > lastReportTimestampNanos) {
			lastReportTimestampNanos = timestampNanos;
		}
		if (toldAgain) {
			return;
		}
		lastLatitude = latitude;
		lastLongitude = longitude;
		lastAccuracyMeters = accuracyMeters;
		newestInUseSeconds = 0;
		setReference(latitude, longitude);
		double accuracy = Math.max(accuracyMeters, MIN_LOCATION_ACCURACY_METERS);
		double accuracyWeight = 1 / (accuracy * accuracy);
		double[] eastNorth = toReference(latitude, longitude);
		add(locations, new double[]{eastNorth[0], eastNorth[1],
				accuracyWeight * FIX_INTERVAL_SECONDS, sessionX, sessionZ, accuracyWeight},
				MAX_LOCATION_READINGS);
	}

	/**
	 * Replaces the landmarks, as the session has them now: it keeps learning where they are.
	 */
	public void setLandmarks(List<Landmark> landmarks) {
		this.landmarks = new ArrayList<>(landmarks);
		if (landmarks.isEmpty()) {
			landmarkOffset = Double.NaN;
			landmarkAccuracyDegrees = Double.NaN;
			heldCompassOffset = Double.NaN;
			return;
		}
		setReference(landmarks.get(0).latitude(), landmarks.get(0).longitude());
		// Against the reference, which stays as it is until the next reset.
		landmarkOffset = landmarkHeading();
		landmarkAccuracyDegrees = landmarkHeadingAccuracyDegrees(landmarkEastNorth());
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
		heldCompassOffset = averageCompassOffset;
		heldCompassAccuracyDegrees = averageCompassAccuracy;
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
				|| !Double.isNaN(landmarkOffset);
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
		return Math.toDegrees(landmarksSetHeading() ? landmarkOffset : compassHeading());
	}

	/**
	 * @return whether the landmarks set the heading rather than the compass, see
	 * getHeadingOffsetDegrees
	 */
	public boolean landmarksSetHeading() {
		if (Double.isNaN(landmarkOffset)) {
			return false;
		}
		double compassAccuracy = Math.max(compassAccuracyDegrees(),
				MAX_COMPARED_COMPASS_ACCURACY_DEGREES);
		return Double.isNaN(compassAccuracy) || landmarkAccuracyDegrees <= compassAccuracy;
	}

	/**
	 * @return how far off getHeadingOffsetDegrees may be, in degrees: as the landmarks' spread
	 * tells when they set it, see landmarkHeadingAccuracyDegrees, else as the compass's readings
	 * do, see compassAccuracyDegrees; NaN without any reading
	 */
	public double headingAccuracyDegrees() {
		return landmarksSetHeading() ? landmarkAccuracyDegrees : compassAccuracyDegrees();
	}

	/**
	 * @return the offset the compass tells, in radians, held while there are landmarks, NaN
	 * without any reading
	 */
	private double compassHeading() {
		return Double.isNaN(heldCompassOffset) ? averageCompassOffset : heldCompassOffset;
	}

	/**
	 * @return how far off the offset the compass tells may be, in degrees, held while there are
	 * landmarks, NaN without any reading
	 */
	public double compassAccuracyDegrees() {
		return Double.isNaN(heldCompassOffset) ? averageCompassAccuracy
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
