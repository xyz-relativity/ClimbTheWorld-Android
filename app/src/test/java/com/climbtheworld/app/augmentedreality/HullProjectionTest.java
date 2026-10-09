package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

public class HullProjectionTest {
	private static final Vector2d ANGLE_OF_VIEW = new Vector2d(60, 80);
	private static final Vector2d VIEW_SIZE = new Vector2d(1000, 1500);
	private static final Vector2d CONTAINER_SIZE = new Vector2d(2000, 2000);
	private static final double EYE_HEIGHT = 1.5;

	private static HullProjection projection(double azimuthDeg, double pitchDeg) {
		HullProjection projection = new HullProjection();
		projection.setCamera(azimuthDeg, pitchDeg,
				AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.x, VIEW_SIZE.x),
				AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.y, VIEW_SIZE.y),
				CONTAINER_SIZE.x / 2, CONTAINER_SIZE.y / 2);
		return projection;
	}

	/**
	 * A line through the points, in meters east and north, with elevations unknown.
	 */
	private static ClimbingHull.GroundLine line(double... eastNorth) {
		Coordinate[] points = new Coordinate[eastNorth.length / 2];
		for (int index = 0; index < points.length; index++) {
			points[index] = new Coordinate(eastNorth[index * 2], eastNorth[index * 2 + 1]);
		}
		return ClimbingHull.GroundLine.along(points, 0, 0, ClimbingHull.METERS_PER_DEGREE, null);
	}

	private static void assertWithinCut(float x, float y) {
		// The cut is CUT_MARGIN beyond the container, around its centre.
		assertTrue("x " + x, Math.abs(x - CONTAINER_SIZE.x / 2) <= CONTAINER_SIZE.x / 2 * 1.2 + 1);
		assertTrue("y " + y, Math.abs(y - CONTAINER_SIZE.y / 2) <= CONTAINER_SIZE.y / 2 * 1.2 + 1);
	}

	@Test
	public void projectsLikeThePois() {
		double[][] cameras = {{0, 0}, {37, 10}, {200, -25}};
		double[][] points = {{0, 100}, {30, 80}, {-40, 300}, {-200, -150}, {500, 400}};
		for (double[] camera : cameras) {
			HullProjection projection = projection(camera[0], camera[1]);
			for (double[] point : points) {
				double distance = Math.hypot(point[0], point[1]);
				double azimuth = Math.toDegrees(Math.atan2(point[0], point[1]));
				double yaw = GeoUtils.diffAngle(azimuth, camera[0]);
				// The ground level with the observer, as for the POIs without elevation.
				double elevationAngle = Math.toDegrees(Math.atan2(-EYE_HEIGHT, distance));
				if (AugmentedRealityUtils.angleFromCameraAxis(yaw, elevationAngle, camera[1])
						> 30) {
					continue;
				}

				// A short segment starting on the point, all in view.
				ClimbingHull.GroundLine segment = line(point[0], point[1],
						point[0] + point[0] / distance, point[1] + point[1] / distance);
				projection.clearLines();
				projection.projectLine(segment, 0, 0, Double.NaN, EYE_HEIGHT);
				Vector4d expected = AugmentedRealityUtils.getXYPosition(yaw, elevationAngle,
						camera[1], 0, 0, new Vector2d(0, 0), ANGLE_OF_VIEW, VIEW_SIZE,
						CONTAINER_SIZE);
				assertEquals(4, projection.getLineCount());
				assertEquals("x of " + point[0] + ", " + point[1], expected.x,
						projection.getLines()[0], 0.05);
				assertEquals("y of " + point[0] + ", " + point[1], expected.y,
						projection.getLines()[1], 0.05);
			}
		}
	}

	@Test
	public void elevationsAreAboveTheEye() {
		HullProjection projection = projection(0, 0);
		ClimbingHull.GroundLine segment = line(0, 100, 0, 101);
		segment.elevation[0] = 120;
		segment.elevation[1] = 120;
		projection.clearLines();
		// The eye 20 meters below the line: 20 / 100 of the focal above the centre.
		projection.projectLine(segment, 0, 0, 100, EYE_HEIGHT);
		double focalY = AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.y, VIEW_SIZE.y);
		assertEquals(CONTAINER_SIZE.y / 2 - focalY * 20 / 100, projection.getLines()[1], 0.05);
	}

	@Test
	public void lineBehindTheCameraIsLeftOut() {
		HullProjection projection = projection(0, 0);
		projection.clearLines();
		projection.projectLine(line(-50, -100, 50, -100), 0, 0, Double.NaN, EYE_HEIGHT);
		assertEquals(0, projection.getLineCount());
	}

	@Test
	public void lineFromBehindTheCameraIsCutToTheView() {
		HullProjection projection = projection(0, -10);
		projection.clearLines();
		// Passing right next to the observer, from behind to far ahead.
		projection.projectLine(line(2, -100, 2, 100), 0, 0, Double.NaN, EYE_HEIGHT);
		assertTrue(projection.getLineCount() > 0);
		float[] lines = projection.getLines();
		for (int index = 0; index < projection.getLineCount(); index += 2) {
			assertTrue(Float.isFinite(lines[index]) && Float.isFinite(lines[index + 1]));
			assertWithinCut(lines[index], lines[index + 1]);
		}
		// Far ahead, it reaches close to the horizon, above the centre as the camera looks down.
		float farY = lines[projection.getLineCount() - 1];
		double focalY = AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.y, VIEW_SIZE.y);
		double horizonY = CONTAINER_SIZE.y / 2 + focalY * Math.tan(Math.toRadians(-10));
		assertTrue(farY > horizonY && farY < horizonY + 30);
	}

	@Test
	public void labelHangsFromTheClosestGroundInView() {
		HullProjection projection = projection(0, 0);
		projection.clearLines();
		// Across the view, 50 meters ahead: flat on screen, but closest straight ahead.
		projection.projectLine(line(-100, 50, 100, 50), 0, 0, Double.NaN, EYE_HEIGHT);

		assertTrue(projection.findLabelAnchor(VIEW_SIZE.x, VIEW_SIZE.y, 0, 50, 30, 10, 100));
		double focalY = AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.y, VIEW_SIZE.y);
		assertEquals(CONTAINER_SIZE.x / 2, projection.getLabelX(), 0.05);
		assertEquals(CONTAINER_SIZE.y / 2 + focalY * EYE_HEIGHT / 50, projection.getLabelY(),
				0.05);

		// Turning the camera to the right keeps it on the same ground.
		HullProjection turned = projection(10, 0);
		turned.clearLines();
		turned.projectLine(line(-100, 50, 100, 50), 0, 0, Double.NaN, EYE_HEIGHT);
		assertTrue(turned.findLabelAnchor(VIEW_SIZE.x, VIEW_SIZE.y, 0, 50, 30, 10, 100));
		double focalX = AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.x, VIEW_SIZE.x);
		assertEquals(CONTAINER_SIZE.x / 2 - focalX * Math.tan(Math.toRadians(10)),
				turned.getLabelX(), 0.05);
	}

	@Test
	public void labelStaysInTheView() {
		HullProjection projection = projection(0, 0);
		projection.clearLines();
		// Starting low on the right, out of the view, heading away.
		projection.projectLine(line(5, 1, 5, 100), 0, 0, Double.NaN, EYE_HEIGHT);
		assertTrue(projection.findLabelAnchor(VIEW_SIZE.x, VIEW_SIZE.y, 0, 50, 30, 10, 100));
		assertTrue(projection.getLabelX() - CONTAINER_SIZE.x / 2 > 0);
		assertTrue(projection.getLabelX() - CONTAINER_SIZE.x / 2 <= VIEW_SIZE.x / 2 - 60);
		assertTrue(projection.getLabelY() - CONTAINER_SIZE.y / 2 + 30 <= VIEW_SIZE.y / 2 - 100);

		// Off to the right of the view, but in it once the screen is turned a quarter.
		projection.clearLines();
		projection.projectLine(line(18.6, 26.8, 25, 26.8), 0, 0, Double.NaN, EYE_HEIGHT);
		assertFalse(projection.findLabelAnchor(VIEW_SIZE.x, VIEW_SIZE.y, 0, 50, 30, 10, 100));
		assertTrue(projection.findLabelAnchor(VIEW_SIZE.x, VIEW_SIZE.y, 90, 50, 30, 10, 100));

		projection.clearLines();
		projection.projectLine(line(-50, -100, 50, -100), 0, 0, Double.NaN, EYE_HEIGHT);
		assertFalse("Behind the camera",
				projection.findLabelAnchor(VIEW_SIZE.x, VIEW_SIZE.y, 0, 50, 30, 10, 100));
	}

	@Test
	public void groundAroundTheObserverFillsTheBottomOfTheView() {
		HullProjection projection = projection(0, -20);
		ClimbingHull.GroundLine square = line(-500, -500, -500, 500, 500, 500, 500, -500,
				-500, -500);
		int corners = projection.projectPolygon(square, 0, 0, Double.NaN, EYE_HEIGHT);
		assertTrue(corners >= 3);

		float[] polygon = projection.getPolygon();
		float lowest = Float.NEGATIVE_INFINITY;
		for (int corner = 0; corner < corners; corner++) {
			assertWithinCut(polygon[corner * 2], polygon[corner * 2 + 1]);
			lowest = Math.max(lowest, polygon[corner * 2 + 1]);
		}
		// Cut along the bottom of the view, and beyond, rather than running off to infinity.
		assertEquals(CONTAINER_SIZE.y / 2 * 2.2, lowest, 1);
	}
}
