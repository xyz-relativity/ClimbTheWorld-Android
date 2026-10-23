package com.climbtheworld.app.navigate.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompassCalibrationMotionTest {
	private static final int SAMPLES = 360;

	@Test
	public void signCrossesItselfAtItsMiddle() {
		assertPoint(0, 0, 0);
		assertPoint(0.5, 0, 0);
		assertPoint(0.25, 1, 0);
		assertPoint(0.75, -1, 0);
	}

	@Test
	public void signStartsUpAndToTheRight() {
		double[] point = new double[2];
		CompassCalibrationMotion.signPoint(0.01, point);
		assertTrue(point[0] > 0);
		// The y axis points down, as on the screen.
		assertTrue(point[1] < 0);
	}

	@Test
	public void signIsWiderThanTallLikeTheInfinitySign() {
		double[] point = new double[2];
		double top = 0;
		double bottom = 0;
		for (int sample = 0; sample < SAMPLES; sample++) {
			CompassCalibrationMotion.signPoint((double) sample / SAMPLES, point);
			top = Math.min(top, point[1]);
			bottom = Math.max(bottom, point[1]);
		}
		double height = bottom - top;
		assertTrue(height > 0.8);
		assertTrue(height < 1.2);
	}

	/**
	 * The magnetometer is only calibrated once it measured the field from every side, so the
	 * screen faces left, right, up and down over a loop, and the phone rolls both ways.
	 */
	@Test
	public void phoneFacesEveryWayOverALoop() {
		double[] turns = new double[3];
		double[] rotation = new double[9];
		double[] screenMin = new double[2];
		double[] screenMax = new double[2];
		double rollMin = 0;
		double rollMax = 0;
		for (int sample = 0; sample < SAMPLES; sample++) {
			CompassCalibrationMotion.phoneTurns((double) sample / SAMPLES, turns);
			CompassCalibrationMotion.rotation(turns, rotation);
			// Where the screen faces is the third column, the turned z axis.
			for (int axis = 0; axis < 2; axis++) {
				screenMin[axis] = Math.min(screenMin[axis], rotation[3 * axis + 2]);
				screenMax[axis] = Math.max(screenMax[axis], rotation[3 * axis + 2]);
			}
			rollMin = Math.min(rollMin, turns[2]);
			rollMax = Math.max(rollMax, turns[2]);
		}
		for (int axis = 0; axis < 2; axis++) {
			assertTrue(screenMin[axis] < -0.7);
			assertTrue(screenMax[axis] > 0.7);
		}
		assertTrue(rollMin < -30);
		assertTrue(rollMax > 30);
	}

	@Test
	public void phoneFacesTheViewerAtTheMiddle() {
		double[] turns = new double[3];
		double[] rotation = new double[9];
		CompassCalibrationMotion.phoneTurns(0, turns);
		CompassCalibrationMotion.rotation(turns, rotation);
		assertEquals(1, rotation[8], 1e-9);
	}

	@Test
	public void rotationTurnsTheScreenAsDocumented() {
		double[] rotation = new double[9];
		// Yaw: the screen towards the phone's right.
		CompassCalibrationMotion.rotation(new double[]{90, 0, 0}, rotation);
		assertEquals(1, rotation[2], 1e-9);
		// Pitch: the top towards the viewer.
		CompassCalibrationMotion.rotation(new double[]{0, 90, 0}, rotation);
		assertEquals(1, rotation[7], 1e-9);
		// Roll: the top to the left, anticlockwise.
		CompassCalibrationMotion.rotation(new double[]{0, 0, 90}, rotation);
		assertEquals(-1, rotation[1], 1e-9);
	}

	private static void assertPoint(double loop, double x, double y) {
		double[] point = new double[2];
		CompassCalibrationMotion.signPoint(loop, point);
		assertEquals(x, point[0], 1e-9);
		assertEquals(y, point[1], 1e-9);
	}
}
