package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionViewAnglesTest {
	// 60 degrees across, 90 up.
	private static final float[] PROJECTION = projection(60, 90);

	private static float[] projection(double horizontalDegrees, double verticalDegrees) {
		float[] projection = new float[16];
		projection[0] = (float) (1 / Math.tan(Math.toRadians(horizontalDegrees / 2)));
		projection[5] = (float) (1 / Math.tan(Math.toRadians(verticalDegrees / 2)));
		projection[10] = -1;
		projection[11] = -1;
		return projection;
	}

	private static float[] vector(double x, double y, double z) {
		return new float[]{(float) x, (float) y, (float) z};
	}

	@Test
	public void readsAnUprightCameraLookingAhead() {
		SessionViewAngles angles = SessionViewAngles.of(vector(1, 0, 0), vector(0, 1, 0),
				vector(0, 0, 1), PROJECTION);

		assertEquals(0, angles.azimuthDegrees, 1e-4);
		assertEquals(0, angles.pitchDegrees, 1e-4);
		assertEquals(0, angles.rollDegrees, 1e-4);
		assertEquals(60, angles.horizontalAngleOfViewDegrees, 1e-3);
		assertEquals(90, angles.verticalAngleOfViewDegrees, 1e-3);
	}

	@Test
	public void readsWhichWayTheCameraLooks() {
		// Looking east, +X, and 20 degrees up.
		double up = Math.toRadians(20);
		SessionViewAngles angles = SessionViewAngles.of(vector(0, 0, 1),
				vector(-Math.sin(up), Math.cos(up), 0), vector(-Math.cos(up), -Math.sin(up), 0),
				PROJECTION);

		assertEquals(90, angles.azimuthDegrees, 1e-4);
		assertEquals(20, angles.pitchDegrees, 1e-4);
		assertEquals(0, angles.rollDegrees, 1e-4);
	}

	@Test
	public void readsTheRollClockwise() {
		// The phone turned 30 degrees clockwise: its up leans right.
		double roll = Math.toRadians(30);
		SessionViewAngles angles = SessionViewAngles.of(
				vector(Math.cos(roll), -Math.sin(roll), 0), vector(Math.sin(roll), Math.cos(roll), 0),
				vector(0, 0, 1), PROJECTION);

		assertEquals(30, angles.rollDegrees, 1e-4);
		assertEquals(0, angles.pitchDegrees, 1e-4);
	}

	@Test
	public void readsAPhoneOnItsSide() {
		// The phone on its side, its top to the left, with the screen not turned: the screen's
		// right is the world's up.
		SessionViewAngles angles = SessionViewAngles.of(vector(0, 1, 0), vector(-1, 0, 0),
				vector(0, 0, 1), projection(90, 60));

		assertEquals(90, Math.abs(angles.rollDegrees), 1e-4);
		assertEquals(90, angles.horizontalAngleOfViewDegrees, 1e-3);
	}
}
