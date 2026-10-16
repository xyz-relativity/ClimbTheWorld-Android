package com.climbtheworld.app.sensors.orientation;

/**
 * Calms the jitter of the rotation vector, which the A.R. view magnifies: at its angle of view,
 * a twitch of half a degree moves the markers by several pixels. Small changes are averaged out,
 * while turning, the averaging all but stops so that the view keeps up.
 *
 * The rotation is averaged as a quaternion rather than as angles, which wrap around and lose
 * their meaning when the device points straight up or down.
 */
class OrientationSmoother {
	// How long small changes are averaged over.
	static final double STILL_SMOOTHING_SECONDS = 0.15;
	// Changes of about this size are taken as jitter, while much bigger ones are taken as turning.
	static final double JITTER_DEGREES = 0.5;

	// x, y, z, w, as in the rotation vector.
	private final float[] smoothed = new float[4];
	private boolean started;
	private long lastTimestampNanos;

	/**
	 * Adds a reading of the rotation vector.
	 *
	 * @param rotationVector the values of the reading, see SensorEvent.values
	 * @param timestampNanos when the reading was taken, see SensorEvent.timestamp
	 * @return the smoothed rotation vector, to read before the next update; it can be passed to
	 * SensorManager.getRotationMatrixFromVector()
	 */
	float[] update(float[] rotationVector, long timestampNanos) {
		double x = rotationVector[0];
		double y = rotationVector[1];
		double z = rotationVector[2];
		// The fourth value is optional, as it follows from the others.
		double w = rotationVector.length > 3 ? rotationVector[3]
				: Math.sqrt(Math.max(0, 1 - x * x - y * y - z * z));

		double weight = 1;
		if (started) {
			double dot = smoothed[0] * x + smoothed[1] * y + smoothed[2] * z + smoothed[3] * w;
			// A quaternion and its opposite are the same rotation, so the reading is turned to
			// whichever of the two is nearer.
			if (dot < 0) {
				x = -x;
				y = -y;
				z = -z;
				w = -w;
				dot = -dot;
			}
			double changeDegrees = Math.toDegrees(2 * Math.acos(Math.min(1, dot)));
			double jitters = changeDegrees / JITTER_DEGREES;
			double smoothingSeconds = STILL_SMOOTHING_SECONDS / (1 + jitters * jitters);
			// Weighted by the time since the previous reading rather than counted, so that the
			// smoothing does not depend on how often the sensor is read.
			double elapsedSeconds = Math.max(0, timestampNanos - lastTimestampNanos) / 1e9;
			weight = 1 - Math.exp(-elapsedSeconds / smoothingSeconds);
		}
		started = true;
		lastTimestampNanos = timestampNanos;

		// Blending the components and normalising the result stays close enough to the exact
		// interpolation for the small steps in between readings.
		double blendedX = smoothed[0] + weight * (x - smoothed[0]);
		double blendedY = smoothed[1] + weight * (y - smoothed[1]);
		double blendedZ = smoothed[2] + weight * (z - smoothed[2]);
		double blendedW = smoothed[3] + weight * (w - smoothed[3]);
		double norm = Math.sqrt(blendedX * blendedX + blendedY * blendedY + blendedZ * blendedZ
				+ blendedW * blendedW);
		smoothed[0] = (float) (blendedX / norm);
		smoothed[1] = (float) (blendedY / norm);
		smoothed[2] = (float) (blendedZ / norm);
		smoothed[3] = (float) (blendedW / norm);
		return smoothed;
	}

	/**
	 * Starts over, from the next reading as it is.
	 */
	void reset() {
		started = false;
	}
}
