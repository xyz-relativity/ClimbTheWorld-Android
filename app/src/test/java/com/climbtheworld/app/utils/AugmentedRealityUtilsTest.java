package com.climbtheworld.app.utils;

import static junit.framework.Assert.assertEquals;

import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Created by xyz on 1/29/18.
 */
@RunWith(MockitoJUnitRunner.class)
public class AugmentedRealityUtilsTest {

	@Test
	public void getXYPosition() {
		Vector2d objSize = new Vector2d(1, 1);
		Vector2d fieldOfViewDeg = new Vector2d(60, 60);

		Vector2d viewSize = new Vector2d(1000, 1000);
		Vector2d containerSize = new Vector2d(2000, 2000);

		for (int i = 0; i <= 360; ++i) {
			Vector4d pos =
					AugmentedRealityUtils.getXYPosition(-10, 0, 0, i, 0, objSize, fieldOfViewDeg,
							viewSize, containerSize);
			System.out.println(pos.x + "," + pos.y + "," + pos.w);
		}
	}

	@Test
	public void getXYPositionSpansAngleOfViewOverView() {
		Vector2d objSize = new Vector2d(0, 0);
		Vector2d fieldOfViewDeg = new Vector2d(60, 80);
		Vector2d viewSize = new Vector2d(1000, 1500);
		Vector2d containerSize = new Vector2d(2000, 2000);

		Vector4d centre =
				AugmentedRealityUtils.getXYPosition(0, 0, 0, 0, 0, objSize, fieldOfViewDeg,
						viewSize, containerSize);
		assertEquals("Centre x", 1000, centre.x, 1e-6);
		assertEquals("Centre y", 1000, centre.y, 1e-6);

		Vector4d rightEdge =
				AugmentedRealityUtils.getXYPosition(30, 0, 0, 0, 0, objSize, fieldOfViewDeg,
						viewSize, containerSize);
		assertEquals("Right edge of the view", 1500, rightEdge.x, 1e-6);
		assertEquals("Right edge of the view, on the horizon", 1000, rightEdge.y, 1e-6);

		Vector4d topEdge =
				AugmentedRealityUtils.getXYPosition(0, 40, 0, 0, 0, objSize, fieldOfViewDeg,
						viewSize, containerSize);
		assertEquals("Top edge of the view", 250, topEdge.y, 1e-6);
	}

	@Test
	public void getXYPositionFollowsCameraPitch() {
		Vector2d objSize = new Vector2d(0, 0);
		Vector2d fieldOfViewDeg = new Vector2d(60, 80);
		Vector2d viewSize = new Vector2d(1000, 1500);
		Vector2d containerSize = new Vector2d(2000, 2000);

		Vector4d aimedAt =
				AugmentedRealityUtils.getXYPosition(0, 25, 25, 0, 0, objSize, fieldOfViewDeg,
						viewSize, containerSize);
		assertEquals("Point on the camera axis x", 1000, aimedAt.x, 1e-6);
		assertEquals("Point on the camera axis y", 1000, aimedAt.y, 1e-6);

		Vector4d horizon =
				AugmentedRealityUtils.getXYPosition(0, 0, -40, 0, 0, objSize, fieldOfViewDeg,
						viewSize, containerSize);
		assertEquals("Horizon of a camera looking down", 250, horizon.y, 1e-6);

		// Off-axis points of a tilted camera are further away than their azimuth alone suggests.
		Vector4d offAxis =
				AugmentedRealityUtils.getXYPosition(30, 0, -40, 0, 0, objSize, fieldOfViewDeg,
						viewSize, containerSize);
		assertEquals("Off axis x", 1000 + 500 / Math.cos(Math.toRadians(40)), offAxis.x, 1e-6);
		assertEquals("Off axis, still on the horizon", 250, offAxis.y, 1e-6);
	}

	@Test
	public void angleFromCameraAxis() {
		assertEquals(0, AugmentedRealityUtils.angleFromCameraAxis(0, 25, 25), 1e-6);
		assertEquals(30, AugmentedRealityUtils.angleFromCameraAxis(30, 0, 0), 1e-6);
		assertEquals(40, AugmentedRealityUtils.angleFromCameraAxis(0, 40, 0), 1e-6);
		assertEquals(180, AugmentedRealityUtils.angleFromCameraAxis(180, 0, 0), 1e-6);
		// Close to the zenith, a large azimuth difference is a small angle.
		assertEquals(Math.toDegrees(Math.acos(0.875)),
				AugmentedRealityUtils.angleFromCameraAxis(60, 60, 60), 1e-6);
	}

	@Test
	public void remapScaleToLog() {
//        for (int i = 0; i<= 500; ++i) {
//            System.out.println(i + ", " + AugmentedRealityUtils.remapScaleToLog(0f, 500f, 200f,
//            5f, i));
//        }
	}

	@Test
	public void remapScale() {
		assertEquals("Negative origin scale Min", 0.0f,
				AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, -30f));
		assertEquals("Negative origin scale Max", 2000f,
				AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, 30f));
		assertEquals("Negative origin scale Mid", 1000f,
				AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, 0f));

		assertEquals("Positive origin scale Min", 0.0f,
				AugmentedRealityUtils.remapScale(0f, 30f, 0f, 2000f, 0f));
		assertEquals("Positive origin scale Max", 2000f,
				AugmentedRealityUtils.remapScale(0f, 30f, 0f, 2000f, 30f));
		assertEquals("Positive origin scale Mid", 1000f,
				AugmentedRealityUtils.remapScale(0f, 30f, 0f, 2000f, 15f));

		assertEquals("Negative dest scale Min", -1000f,
				AugmentedRealityUtils.remapScale(0f, 30f, -1000f, 1000f, 0f));
		assertEquals("Negative dest scale Max", 1000f,
				AugmentedRealityUtils.remapScale(0f, 30f, -1000f, 1000f, 30f));
		assertEquals("Negative dest scale Mid", 0f,
				AugmentedRealityUtils.remapScale(0f, 30f, -1000f, 1000f, 15f));

		assertEquals("Under scale", -333.33334f,
				AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, -40f));
		assertEquals("Over scale", 2333.3333f,
				AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, 40f));
	}
}