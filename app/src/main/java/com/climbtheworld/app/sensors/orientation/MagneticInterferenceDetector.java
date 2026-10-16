package com.climbtheworld.app.sensors.orientation;

/**
 * Tells whether something near the device bends the earth's magnetic field, such as steel, a car
 * or a magnet, by comparing the strength and the dip of the field the magnetometer measures with
 * those the earth has at the observer. The compass then points the wrong way, however well it is
 * calibrated.
 */
class MagneticInterferenceDetector {
	// Magnetometers are off by a few percent, while metal close enough to turn the compass by
	// tens of degrees changes the field by much more.
	static final double DISTURBED_STRENGTH_DEVIATION = 0.25;
	// A disturbance can bend the field while leaving its strength about right, so the angle it
	// dips below the horizon is checked as well. Measured, it is off by a few degrees at most.
	static final double DISTURBED_DIP_DEGREES = 12;
	// Lower, so that a field hovering around the limits does not keep switching.
	static final double UNDISTURBED_STRENGTH_DEVIATION = 0.15;
	static final double UNDISTURBED_DIP_DEGREES = 8;
	// Readings are averaged over about this long, so that a single noisy one does not count, and
	// nothing is judged before they cover it.
	static final long SMOOTHING_NANOS = 1_000_000_000L;

	private double smoothedMicroTesla = Double.NaN;
	private double smoothedDipDegrees;
	private long firstTimestampNanos;
	private long lastTimestampNanos;
	private boolean disturbed;

	/**
	 * Strength of a magnetic field, from its components along the three axes.
	 */
	static double strength(float[] values) {
		return Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
	}

	/**
	 * Angle of a magnetic field below the horizon, positive downwards like
	 * GeomagneticField.getInclination().
	 *
	 * @param field          the field in device coordinates, as the magnetometer measures it
	 * @param rotationMatrix the orientation of the device, 3x3 or 4x4, see
	 *                       SensorManager.getRotationMatrixFromVector()
	 */
	static double dipDegrees(float[] field, float[] rotationMatrix) {
		// Up, in device coordinates, is the third row of the rotation matrix.
		int up = rotationMatrix.length == 16 ? 8 : 6;
		double fieldUp = field[0] * rotationMatrix[up] + field[1] * rotationMatrix[up + 1]
				+ field[2] * rotationMatrix[up + 2];
		double sine = -fieldUp / strength(field);
		return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sine))));
	}

	/**
	 * Adds a reading of the field.
	 *
	 * @param expectedMicroTesla the strength of the earth's field at the observer
	 * @param expectedDipDegrees the dip of the earth's field at the observer
	 * @param timestampNanos     when the reading was taken, see SensorEvent.timestamp
	 * @return whether the field is disturbed
	 */
	boolean update(double measuredMicroTesla, double expectedMicroTesla, double measuredDipDegrees,
	               double expectedDipDegrees, long timestampNanos) {
		if (Double.isNaN(smoothedMicroTesla)) {
			smoothedMicroTesla = measuredMicroTesla;
			smoothedDipDegrees = measuredDipDegrees;
			firstTimestampNanos = timestampNanos;
		} else {
			// Weighted by the time since the previous reading rather than counted, as readings
			// often come faster than asked for, for instance when the rotation vector also reads
			// the magnetometer at a higher rate.
			long elapsedNanos = Math.max(0, timestampNanos - lastTimestampNanos);
			double weight = 1 - Math.exp(-(double) elapsedNanos / SMOOTHING_NANOS);
			smoothedMicroTesla += weight * (measuredMicroTesla - smoothedMicroTesla);
			smoothedDipDegrees += weight * (measuredDipDegrees - smoothedDipDegrees);
		}
		lastTimestampNanos = timestampNanos;

		if (timestampNanos - firstTimestampNanos < SMOOTHING_NANOS) {
			return disturbed;
		}
		double strengthDeviation = Math.abs(smoothedMicroTesla / expectedMicroTesla - 1);
		double dipDeviation = Math.abs(smoothedDipDegrees - expectedDipDegrees);
		if (strengthDeviation > DISTURBED_STRENGTH_DEVIATION
				|| dipDeviation > DISTURBED_DIP_DEGREES) {
			disturbed = true;
		} else if (strengthDeviation < UNDISTURBED_STRENGTH_DEVIATION
				&& dipDeviation < UNDISTURBED_DIP_DEGREES) {
			disturbed = false;
		}
		return disturbed;
	}

	boolean isDisturbed() {
		return disturbed;
	}

	void reset() {
		smoothedMicroTesla = Double.NaN;
		disturbed = false;
	}
}
