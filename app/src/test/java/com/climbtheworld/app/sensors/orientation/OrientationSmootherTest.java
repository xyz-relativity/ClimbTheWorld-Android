package com.climbtheworld.app.sensors.orientation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrientationSmootherTest {
	// The A.R. view reads the rotation vector at 50 Hz.
	private static final int READINGS_PER_SECOND = 50;
	private static final long READING_INTERVAL_NANOS = 1_000_000_000L / READINGS_PER_SECOND;

	private final OrientationSmoother smoother = new OrientationSmoother();
	private long timestampNanos;

	@Test
	public void firstReadingIsTakenAsItIs() {
		assertEquals(0, angleDegrees(read(30), 30), 1e-3);
	}

	@Test
	public void missingFourthValueIsWorkedOut() {
		float[] smoothed = smoother.update(new float[]{0, 0, (float) Math.sin(Math.toRadians(15))},
				0);

		assertEquals(0, angleDegrees(smoothed, 30), 1e-3);
	}

	@Test
	public void jitterIsCalmed() {
		read(0);
		double largest = 0;
		for (int reading = 0; reading < 3 * READINGS_PER_SECOND; reading++) {
			float[] smoothed = read(reading % 2 == 0 ? 0.2 : -0.2);
			if (reading > READINGS_PER_SECOND) {
				largest = Math.max(largest, angleDegrees(smoothed, 0));
			}
		}

		// Half the jitter at most; about a tenth of it in practice.
		assertTrue(largest < 0.1);
	}

	@Test
	public void turningIsFollowedClosely() {
		assertTrue(lagWhileTurning(30) < 1);
		assertTrue(lagWhileTurning(90) < 0.5);
	}

	@Test
	public void suddenTurnIsFollowedAtOnce() {
		read(0);
		read(0);

		assertTrue(angleDegrees(read(10), 10) < 0.5);
	}

	@Test
	public void oppositeQuaternionIsTheSameRotation() {
		read(30);
		double half = Math.toRadians(30) / 2;
		timestampNanos += READING_INTERVAL_NANOS;
		float[] smoothed = smoother.update(
				new float[]{0, 0, (float) -Math.sin(half), (float) -Math.cos(half)}, timestampNanos);

		assertEquals(0, angleDegrees(smoothed, 30), 1e-3);
	}

	@Test
	public void resetStartsOverFromTheNextReading() {
		read(0);
		smoother.reset();

		assertEquals(0, angleDegrees(read(5), 5), 1e-3);
	}

	/**
	 * How far behind the smoothed rotation is after turning at a steady speed for two seconds.
	 */
	private double lagWhileTurning(double degreesPerSecond) {
		smoother.reset();
		double turned = 0;
		float[] smoothed = null;
		for (int reading = 0; reading < 2 * READINGS_PER_SECOND; reading++) {
			turned = degreesPerSecond * reading / READINGS_PER_SECOND;
			smoothed = read(turned);
		}
		return angleDegrees(smoothed, turned);
	}

	/**
	 * Feeds the rotation vector of a device turned by the given angle around its Z axis, a reading
	 * after the previous one.
	 */
	private float[] read(double degrees) {
		double half = Math.toRadians(degrees) / 2;
		timestampNanos += READING_INTERVAL_NANOS;
		return smoother.update(new float[]{0, 0, (float) Math.sin(half), (float) Math.cos(half)},
				timestampNanos);
	}

	/**
	 * Angle between a rotation vector and a turn by the given angle around the Z axis, from the
	 * rotation between them, which stays accurate for the small angles that matter here.
	 */
	private static double angleDegrees(float[] rotationVector, double degrees) {
		double half = Math.toRadians(degrees) / 2;
		double x = rotationVector[0];
		double y = rotationVector[1];
		double z = rotationVector[2];
		double w = rotationVector[3];
		// The inverse of the turn, times the rotation vector.
		double cos = Math.cos(half);
		double sin = Math.sin(half);
		double betweenW = cos * w + sin * z;
		double betweenX = cos * x + sin * y;
		double betweenY = cos * y - sin * x;
		double betweenZ = cos * z - sin * w;
		double vector = Math.sqrt(betweenX * betweenX + betweenY * betweenY + betweenZ * betweenZ);
		return Math.toDegrees(2 * Math.atan2(vector, Math.abs(betweenW)));
	}
}
