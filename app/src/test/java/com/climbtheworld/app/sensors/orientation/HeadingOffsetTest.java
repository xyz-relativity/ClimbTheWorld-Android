package com.climbtheworld.app.sensors.orientation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeadingOffsetTest {
	// The compass is read at the rate the screen asks for, about 15 times a second.
	private static final int READINGS_PER_SECOND = 15;
	private static final long READING_INTERVAL_NANOS = 1_000_000_000L / READINGS_PER_SECOND;
	private static final float[] IDENTITY = {
			1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1};
	// Held up, screen facing the user, with the top of the device pointing at the sky: the
	// device's Y axis is up and its Z axis points at the user, towards -Y of the world.
	private static final float[] HELD_UP = {
			1, 0, 0, 0,
			0, 0, -1, 0,
			0, 1, 0, 0,
			0, 0, 0, 1};

	private final HeadingOffset offset = new HeadingOffset();
	private long timestampNanos;

	/**
	 * A device flat on a table, its top at the given azimuth from north: the rotation matrix the
	 * sensor gives, mapping device axes to world axes, each column a device axis.
	 */
	private static float[] flatAt(double azimuthDegrees) {
		double sin = Math.sin(Math.toRadians(azimuthDegrees));
		double cos = Math.cos(Math.toRadians(azimuthDegrees));
		return new float[]{
				(float) cos, (float) sin, 0, 0,
				(float) -sin, (float) cos, 0, 0,
				0, 0, 1, 0,
				0, 0, 0, 1};
	}

	/**
	 * A rotation matrix as another world has it, in which azimuths are the given angle more than
	 * in this one.
	 */
	private static float[] inWorldTurnedBy(float[] rotationMatrix, double azimuthsMoreDegrees) {
		float[] turn = flatAt(azimuthsMoreDegrees);
		float[] turned = new float[16];
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				float sum = 0;
				for (int k = 0; k < 4; k++) {
					sum += turn[row * 4 + k] * rotationMatrix[k * 4 + column];
				}
				turned[row * 4 + column] = sum;
			}
		}
		return turned;
	}

	private static float[] as3x3(float[] rotationMatrix) {
		float[] matrix = new float[9];
		for (int row = 0; row < 3; row++) {
			System.arraycopy(rotationMatrix, row * 4, matrix, row * 3, 3);
		}
		return matrix;
	}

	private static double azimuthDegrees(float[] rotationMatrix) {
		return Math.toDegrees(Math.atan2(rotationMatrix[1], rotationMatrix[5]));
	}

	private static double wrap(double degrees) {
		return ((degrees % 360) + 360) % 360;
	}

	private void add(double degrees, double accuracyDegrees) {
		timestampNanos += READING_INTERVAL_NANOS;
		offset.add(degrees, accuracyDegrees, timestampNanos);
	}

	private void addFor(double seconds, double degrees, double accuracyDegrees) {
		for (int reading = 0; reading < seconds * READINGS_PER_SECOND; reading++) {
			add(degrees, accuracyDegrees);
		}
	}

	@Test
	public void yawIsWhatTurnsGameAzimuthsIntoMagneticOnes() {
		float[] game = flatAt(50);
		float[] magnetic = inWorldTurnedBy(game, 30);

		assertEquals(80, azimuthDegrees(magnetic), 1e-3);
		assertEquals(30, HeadingOffset.yawDegrees(magnetic, game), 1e-3);
		assertEquals(0, HeadingOffset.yawDegrees(game, game), 1e-3);
	}

	@Test
	public void yawWrapsAroundNorth() {
		float[] game = flatAt(170);
		// At 190 degrees, which the sensor reports as -170.
		float[] magnetic = inWorldTurnedBy(game, 20);

		assertEquals(-170, azimuthDegrees(magnetic), 1e-3);
		assertEquals(20, HeadingOffset.yawDegrees(magnetic, game), 1e-3);
		assertEquals(-20, HeadingOffset.yawDegrees(game, magnetic), 1e-3);
	}

	@Test
	public void yawIsReadWhileTheDevicePointsUp() {
		// The azimuth of the top of the device means nothing here, the yaw still does.
		float[] magnetic = inWorldTurnedBy(HELD_UP, 30);

		assertEquals(30, HeadingOffset.yawDegrees(magnetic, HELD_UP), 1e-3);
	}

	@Test
	public void yawIsReadFrom3x3MatricesToo() {
		float[] game = flatAt(50);
		float[] magnetic = inWorldTurnedBy(game, 30);

		assertEquals(30, HeadingOffset.yawDegrees(as3x3(magnetic), as3x3(game)), 1e-3);
		assertEquals(30, HeadingOffset.yawDegrees(as3x3(magnetic), game), 1e-3);
		assertEquals(0, HeadingOffset.yawDegrees(as3x3(IDENTITY), IDENTITY), 1e-3);
	}

	@Test
	public void unknownUntilAReadingIsAdded() {
		assertFalse(offset.isKnown());
		assertTrue(Double.isNaN(offset.getDegrees()));

		add(30, 5);

		assertTrue(offset.isKnown());
		assertEquals(30, offset.getDegrees(), 1e-9);
	}

	@Test
	public void earlyReadingsCountAtOnce() {
		add(20, 5);
		addFor(2, 30, 5);

		// About the mean of the readings so far.
		assertEquals(30, offset.getDegrees(), 1);
	}

	@Test
	public void laterReadingsMoveItSlowly() {
		addFor(2 * HeadingOffset.SMOOTHING_SECONDS, 20, 5);
		addFor(5, 50, 5);

		// Five seconds of a 20 second average: about a fifth of the way.
		double moved = offset.getDegrees() - 20;
		assertTrue(moved > 4 && moved < 10);

		addFor(4 * HeadingOffset.SMOOTHING_SECONDS, 50, 5);
		assertEquals(50, offset.getDegrees(), 1);
	}

	@Test
	public void averagesAcrossNorth() {
		add(359, 5);
		addFor(2, 1, 5);

		assertEquals(0, wrap(offset.getDegrees()), 1);
	}

	@Test
	public void poorReadingsCountForLess() {
		addFor(2 * HeadingOffset.SMOOTHING_SECONDS, 20, 5);
		addFor(5, 50, 30);

		// Three times less accurate than a full-weight reading: nine times less.
		double moved = offset.getDegrees() - 20;
		assertTrue(moved > 0 && moved < 1.5);
	}

	@Test
	public void readingsWithoutAnAccuracyCountAsRough() {
		addFor(2 * HeadingOffset.SMOOTHING_SECONDS, 20, 5);
		addFor(5, 50, Double.NaN);

		double moved = offset.getDegrees() - 20;
		assertTrue(moved > 1 && moved < 5);
	}

	@Test
	public void smoothingDoesNotDependOnTheRate() {
		HeadingOffset slow = new HeadingOffset();
		long slowTimestamp = 0;
		for (int reading = 0; reading < 20 * 5; reading++) {
			slowTimestamp += 200_000_000L;
			slow.add(20, 5, slowTimestamp);
		}
		for (int reading = 0; reading < 5 * 5; reading++) {
			slowTimestamp += 200_000_000L;
			slow.add(50, 5, slowTimestamp);
		}
		addFor(20, 20, 5);
		addFor(5, 50, 5);

		assertEquals(offset.getDegrees(), slow.getDegrees(), 0.5);
	}

	@Test
	public void takesReadingsOnlyFromASteadyCompass() {
		assertTrue(HeadingOffset.canTake(OrientationManager.CompassStatus.OK, 5));
		assertTrue(HeadingOffset.canTake(OrientationManager.CompassStatus.OK, Double.NaN));
		assertFalse(HeadingOffset.canTake(OrientationManager.CompassStatus.OK, 45));
		assertFalse(HeadingOffset.canTake(OrientationManager.CompassStatus.DISTURBED, 5));
		assertFalse(HeadingOffset.canTake(OrientationManager.CompassStatus.NEEDS_CALIBRATION, 5));
	}

	@Test
	public void resetForgetsTheReadings() {
		addFor(5, 20, 5);
		offset.reset();

		assertFalse(offset.isKnown());
		assertTrue(Double.isNaN(offset.getDegrees()));
		add(50, 5);
		assertEquals(50, offset.getDegrees(), 1e-9);
	}
}
