package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScreenPointTest {
	private static final int WIDTH = 1000;
	private static final int HEIGHT = 2000;
	// Tangent of half the horizontal angle of view.
	private static final float FOCAL = 2;

	/**
	 * A projection with the camera at the origin looking down -Z, turned clockwise by roll on
	 * the screen, column-major.
	 */
	private static float[] camera(double rollDegrees) {
		float aspect = (float) WIDTH / HEIGHT;
		float[] projection = {FOCAL, 0, 0, 0, 0, FOCAL * aspect, 0, 0, 0, 0, -1, -1, 0, 0, -0.2f, 0};
		// Rolling the phone clockwise turns the world counter-clockwise in the view.
		float cos = (float) Math.cos(Math.toRadians(rollDegrees));
		float sin = (float) Math.sin(Math.toRadians(rollDegrees));
		float[] view = {cos, sin, 0, 0, -sin, cos, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		float[] result = new float[16];
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				float sum = 0;
				for (int k = 0; k < 4; k++) {
					sum += projection[k * 4 + row] * view[column * 4 + k];
				}
				result[column * 4 + row] = sum;
			}
		}
		return result;
	}

	@Test
	public void putsAPointAheadInTheMiddle() {
		ScreenPoint point = ScreenPoint.project(camera(0), new double[]{0, 0, -10}, WIDTH, HEIGHT);

		assertEquals(WIDTH / 2f, point.x, 1e-3);
		assertEquals(HEIGHT / 2f, point.y, 1e-3);
		assertEquals(0, point.uprightDegrees, 1e-3);
	}

	@Test
	public void putsPointsRightAndUpWhereTheyShow() {
		ScreenPoint point = ScreenPoint.project(camera(0), new double[]{2, 1, -10}, WIDTH, HEIGHT);

		assertTrue(point.x > WIDTH / 2f);
		assertTrue(point.y < HEIGHT / 2f);
	}

	@Test
	public void leavesOutPointsBehindTheCamera() {
		assertNull(ScreenPoint.project(camera(0), new double[]{0, 0, 10}, WIDTH, HEIGHT));
	}

	@Test
	public void turnsThePinUprightAsThePhoneRolls() {
		// Up in the world shows to the right once the view turned a quarter counter-clockwise.
		ScreenPoint point = ScreenPoint.project(camera(-90), new double[]{0, 0, -10}, WIDTH,
				HEIGHT);

		assertEquals(90, point.uprightDegrees, 1e-2);
	}
}
