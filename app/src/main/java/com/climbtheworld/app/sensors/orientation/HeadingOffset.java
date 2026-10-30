package com.climbtheworld.app.sensors.orientation;

/**
 * Where north is in the game rotation vector's world, as the compass tells it, averaged slowly.
 * <p>
 * The game rotation vector fuses the gyroscope and the accelerometer only: it is steady from one
 * reading to the next, but which way its horizontal axes point is arbitrary, and drifts slowly, as
 * the gyroscope does. The rotation vector adds the magnetometer, so its azimuths are from magnetic
 * north, but they follow every bend of the field: steel, a car, the rock itself. Here the yaw
 * between the two worlds is taken from each pair of readings, see yawDegrees, and averaged over
 * SMOOTHING_SECONDS, weighted by how accurate the device says the heading is, so that the
 * orientation turns with the gyroscope and the compass only sets, slowly, where north is. While
 * the field is disturbed, the magnetometer needs calibrating or the device turns fast, the
 * readings are left out and the offset is held as it is, see canTake. Until the compass has told
 * anything, a reading is taken whatever its state: a rough north beats none.
 * <p>
 * How much the readings scatter around the offset tells how far it is to be trusted, see
 * spreadDegrees: a compass that agrees with itself as the user walks about has settled, one that
 * tells north now here, now there, as the field bends around what they walk past, has not. The
 * device's own estimate of its heading accuracy knows nothing of such bends.
 */
class HeadingOffset {
	// How long the readings are averaged over, once they have been coming for that long: before,
	// over the time they have been coming, so that the first ones count at once.
	static final double SMOOTHING_SECONDS = 20;
	// Readings this accurate or better count fully, less accurate ones for less, by the square of
	// how much less.
	static final double FULL_WEIGHT_ACCURACY_DEGREES = 10;
	// How accurate a reading counts as when the device does not tell.
	static final double DEFAULT_ACCURACY_DEGREES = 15;
	// The compass lags behind the gyroscope, and the two readings of a pair are not taken at quite
	// the same time, so the yaw between them is off while the device turns faster than this.
	static final double MAX_TURN_RATE_DEGREES_PER_SECOND = 20;
	// How long readings must have been coming before how much they scatter tells anything: a
	// few readings always agree.
	static final double SPREAD_KNOWN_SECONDS = 5;
	// A gap between readings counts as at most this long towards that: while the compass is not
	// read, see canTake, no readings are coming.
	static final double MAX_READ_GAP_SECONDS = 0.5;
	// How fast the game rotation vector's world may turn away from north while the compass is
	// not read, as the gyroscope drifts.
	static final double DRIFT_DEGREES_PER_SECOND = 0.05;
	// How far off the offset counts as while nothing tells: as far as can be.
	static final double UNKNOWN_DEGREES = 180;

	// Sine and cosine of the offset, averaged.
	private double sin;
	private double cos;
	private boolean known;
	private long firstTimestampNanos;
	private long lastTimestampNanos;
	// How long readings have been coming for, gaps cut short, see MAX_READ_GAP_SECONDS.
	private double readSeconds;

	/**
	 * The yaw from the game rotation vector's world to the rotation vector's, in degrees: what to
	 * add to an azimuth in the game world to get one from magnetic north. Both worlds share their
	 * up, so the rotation between them is about the vertical, and it is read off that rotation
	 * rather than off the azimuth of some axis of the device, which means nothing while the device
	 * points up or down.
	 *
	 * @param magneticRotationMatrix the rotation vector's, 3x3 or 4x4, see
	 *                               SensorManager.getRotationMatrixFromVector()
	 * @param gameRotationMatrix     the game rotation vector's, the same way
	 */
	static double yawDegrees(float[] magneticRotationMatrix, float[] gameRotationMatrix) {
		int magneticStride = magneticRotationMatrix.length == 16 ? 4 : 3;
		int gameStride = gameRotationMatrix.length == 16 ? 4 : 3;
		// The rotation between the worlds is M = magnetic * transpose(game), of which only the
		// horizontal part is needed: a turn about the vertical by phi has cos phi on its diagonal
		// and -sin phi above it.
		double[][] between = new double[2][2];
		for (int row = 0; row < 2; row++) {
			for (int column = 0; column < 2; column++) {
				double sum = 0;
				for (int axis = 0; axis < 3; axis++) {
					sum += magneticRotationMatrix[row * magneticStride + axis]
							* gameRotationMatrix[column * gameStride + axis];
				}
				between[row][column] = sum;
			}
		}
		// A turn of the world by phi, from east towards north, turns azimuths by -phi.
		return Math.toDegrees(Math.atan2(between[0][1] - between[1][0],
				between[0][0] + between[1][1]));
	}

	/**
	 * @param status               what the compass is worth right now
	 * @param turnRateDegreesPerSecond how fast the device turns, NaN when not known yet
	 * @return whether a reading taken now tells where north is
	 */
	static boolean canTake(OrientationManager.CompassStatus status,
	                       double turnRateDegreesPerSecond) {
		return status == OrientationManager.CompassStatus.OK
				&& !(turnRateDegreesPerSecond > MAX_TURN_RATE_DEGREES_PER_SECOND);
	}

	/**
	 * Adds a reading of the offset.
	 *
	 * @param offsetDegrees   see yawDegrees
	 * @param accuracyDegrees how far off the compass may be, NaN when the device does not tell
	 * @param timestampNanos  when the reading was taken, see SensorEvent.timestamp
	 */
	void add(double offsetDegrees, double accuracyDegrees, long timestampNanos) {
		double offset = Math.toRadians(offsetDegrees);
		if (!known) {
			sin = Math.sin(offset);
			cos = Math.cos(offset);
			known = true;
			firstTimestampNanos = timestampNanos;
			lastTimestampNanos = timestampNanos;
			readSeconds = 0;
			return;
		}
		double accuracy = Double.isNaN(accuracyDegrees) ? DEFAULT_ACCURACY_DEGREES
				: accuracyDegrees;
		double ratio = FULL_WEIGHT_ACCURACY_DEGREES / accuracy;
		double accuracyWeight = Math.min(1, ratio * ratio);
		// Weighted by the time since the previous reading rather than counted, so that the
		// smoothing does not depend on how often the sensor is read, and by the accuracy, so
		// that a poor reading counts as a shorter time.
		double elapsedSeconds = Math.max(0, timestampNanos - lastTimestampNanos) / 1e9;
		double comingSeconds = Math.max(0, timestampNanos - firstTimestampNanos) / 1e9;
		double smoothingSeconds = Math.max(elapsedSeconds,
				Math.min(SMOOTHING_SECONDS, comingSeconds));
		double weight = 1 - Math.exp(-elapsedSeconds * accuracyWeight / smoothingSeconds);
		lastTimestampNanos = timestampNanos;
		readSeconds += Math.min(elapsedSeconds, MAX_READ_GAP_SECONDS);

		sin += weight * (Math.sin(offset) - sin);
		cos += weight * (Math.cos(offset) - cos);
	}

	/**
	 * @return whether the compass has told where north is since the last reset
	 */
	boolean isKnown() {
		return known;
	}

	/**
	 * @return the offset, in degrees, NaN until a reading was added
	 */
	double getDegrees() {
		return known ? Math.toDegrees(Math.atan2(sin, cos)) : Double.NaN;
	}

	/**
	 * How much the readings scatter around the offset: the circular standard deviation of the
	 * readings averaged, read off how far the averaged sine and cosine fall short of a unit
	 * vector. Readings that agree leave it near 0; readings spread evenly around leave it at
	 * UNKNOWN_DEGREES.
	 *
	 * @return in degrees, NaN until readings have been coming for SPREAD_KNOWN_SECONDS
	 */
	double spreadDegrees() {
		if (!known || readSeconds < SPREAD_KNOWN_SECONDS) {
			return Double.NaN;
		}
		double length = Math.hypot(sin, cos);
		if (length >= 1) {
			return 0;
		}
		if (length <= 0) {
			return UNKNOWN_DEGREES;
		}
		return Math.min(UNKNOWN_DEGREES, Math.toDegrees(Math.sqrt(-2 * Math.log(length))));
	}

	/**
	 * How far off the offset may be: the spread of the readings, plus how far the gyroscope may
	 * have drifted since the last one, see DRIFT_DEGREES_PER_SECOND, as the compass is not read
	 * while it is disturbed or the device turns fast, see canTake.
	 *
	 * @param nowNanos when the offset is used, as the orientation reading's timestamp, see
	 *                 SensorEvent.timestamp
	 * @return in degrees, UNKNOWN_DEGREES until the spread is known, see spreadDegrees
	 */
	double uncertaintyDegrees(long nowNanos) {
		double spread = spreadDegrees();
		if (Double.isNaN(spread)) {
			return UNKNOWN_DEGREES;
		}
		double sinceLastSeconds = Math.max(0, nowNanos - lastTimestampNanos) / 1e9;
		return Math.min(UNKNOWN_DEGREES, spread + sinceLastSeconds * DRIFT_DEGREES_PER_SECOND);
	}

	/**
	 * Forgets the readings, for when the game rotation vector's world may have drifted away while
	 * the sensors were not read.
	 */
	void reset() {
		known = false;
	}
}
