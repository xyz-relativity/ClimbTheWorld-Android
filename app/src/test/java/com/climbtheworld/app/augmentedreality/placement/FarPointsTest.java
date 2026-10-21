package com.climbtheworld.app.augmentedreality.placement;

import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.CENTRE_X;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.CENTRE_Y;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.FOCAL_LENGTH;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.HEIGHT;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.PRINCIPAL_POINT;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.WIDTH;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.cameraAt;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.flat;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.frame;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.plane;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ViewRay, WallPlane and RayCrossing: the cursor past the depth.
 */
public class FarPointsTest {
	private static final double CENTIMETER = 0.01;

	private static double angleDegrees(double[] a, double[] b) {
		return Math.toDegrees(Math.acos(Math.min(1, CursorHit.dot(a, b)
				/ (CursorHit.length(a) * CursorHit.length(b)))));
	}

	/**
	 * A ray from a point towards another.
	 */
	private static ViewRay ray(double[] from, double[] to) {
		return new ViewRay(from, CursorHit.subtract(to, from));
	}

	@Test
	public void castsTheRayThroughATexturePixel() {
		// The texture is ten times the depth image, see DepthTestFrames.
		ViewRay ahead = ViewRay.throughTexture(PRINCIPAL_POINT[0], PRINCIPAL_POINT[1],
				FOCAL_LENGTH, PRINCIPAL_POINT, cameraAt(1, 2, 3));
		assertArrayEquals(new double[]{1, 2, 3}, ahead.origin, 1e-6);
		assertArrayEquals(new double[]{0, 0, -1}, ahead.direction, 1e-9);

		// Up in the image, 45 degrees.
		ViewRay up = ViewRay.throughTexture(PRINCIPAL_POINT[0],
				PRINCIPAL_POINT[1] - FOCAL_LENGTH[1], FOCAL_LENGTH, PRINCIPAL_POINT,
				cameraAt(0, 0, 0));
		assertEquals(45, angleDegrees(up.direction, new double[]{0, 0, -1}), 1e-6);
		assertTrue(up.direction[1] > 0);
	}

	@Test
	public void fitsTheWallAroundTheCrosshair() {
		// Leaning back 20 degrees, 10 m away.
		double lean = Math.toRadians(20);
		double[] normal = {0, Math.sin(lean), Math.cos(lean)};
		DepthFrame frame = frame(plane(new double[]{0, 0, -10}, normal), cameraAt(0, 0, 0));

		WallPlane wall = WallPlane.fit(frame, CENTRE_X, CENTRE_Y);

		assertTrue(angleDegrees(normal, wall.normal) < 1);
		assertEquals(0, CursorHit.dot(CursorHit.subtract(wall.point,
				new double[]{0, 0, -10}), normal), CENTIMETER);
	}

	@Test
	public void carriesTheCursorUpTheWall() {
		// A wall square on, 10 m away; the ray goes up it to 25 m high.
		WallPlane wall = WallPlane.fit(frame(flat(10), cameraAt(0, 0, 0)), CENTRE_X, CENTRE_Y);
		CursorHit hit = wall.intersect(ray(new double[]{0, 0, 0}, new double[]{0, 25, -10}));

		assertArrayEquals(new double[]{0, 25, -10}, hit.point, CENTIMETER);
		assertEquals(Math.hypot(25, 10), hit.distance, CENTIMETER);
		assertEquals(CursorHit.Source.WALL_PLANE, hit.source);
		assertArrayEquals(new double[]{0, 0, 1}, hit.normal, 1e-6);
	}

	@Test
	public void fitsNoWallTooFar() {
		assertNull(WallPlane.fit(frame(flat(WallPlane.MAX_SOURCE_METERS + 1), cameraAt(0, 0, 0)),
				CENTRE_X, CENTRE_Y));
	}

	@Test
	public void fitsNoWallOnRoughRock() {
		short[] depth = flat(10);
		// Every other column a meter back.
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x += 4) {
				depth[y * WIDTH + x] = 11000;
				depth[y * WIDTH + x + 1] = 11000;
			}
		}

		assertNull(WallPlane.fit(frame(depth, cameraAt(0, 0, 0)), CENTRE_X, CENTRE_Y));
	}

	@Test
	public void missesTheWallBehindOrGlancing() {
		WallPlane wall = WallPlane.fit(frame(flat(10), cameraAt(0, 0, 0)), CENTRE_X, CENTRE_Y);

		assertNull(wall.intersect(ray(new double[]{0, 0, 0}, new double[]{0, 0, 10})));
		// 5 degrees off the wall.
		double glancing = Math.toRadians(5);
		assertNull(wall.intersect(new ViewRay(new double[]{0, 0, 0},
				new double[]{0, Math.cos(glancing), -Math.sin(glancing)})));
	}

	@Test
	public void crossesTwoSightingsAtTheSpot() {
		double[] top = {3, 24, -12};
		ViewRay first = ray(new double[]{0, 1.5, 0}, top);
		ViewRay second = ray(new double[]{4, 1.5, 0.5}, top);

		CursorHit hit = RayCrossing.cross(first, second, null);

		assertArrayEquals(top, hit.point, 1e-6);
		assertEquals(CursorHit.length(CursorHit.subtract(top, second.origin)), hit.distance,
				1e-6);
		assertEquals(CursorHit.Source.SIGHTING, hit.source);
		// About 9 degrees apart, 25 m away: about a meter.
		assertEquals(1, hit.uncertaintyMeters, 0.2);
	}

	@Test
	public void keepsATriangulatedSpotWhereItsAnchorIs() {
		double[] top = {3, 24, -12};
		CursorHit fixed = RayCrossing.cross(ray(new double[]{0, 1.5, 0}, top),
				ray(new double[]{4, 1.5, 0.5}, top), null);
		// ARCore moved the anchor a little as it learned more of the world, and the user walked.
		double[] anchored = {3.1, 24, -12};
		double[] camera = {10, 1.5, 2};

		CursorHit seen = fixed.seenFrom(anchored, camera);

		assertArrayEquals(anchored, seen.point, 1e-9);
		assertEquals(CursorHit.length(CursorHit.subtract(anchored, camera)), seen.distance, 1e-9);
		assertEquals(CursorHit.Source.SIGHTING, seen.source);
		assertEquals(fixed.uncertaintyMeters, seen.uncertaintyMeters, 1e-9);
		assertArrayEquals(fixed.normal, seen.normal, 1e-9);
	}

	@Test
	public void takesTheMiddleOfSightingsThatMiss() {
		double[] top = {0, 20, -15};
		ViewRay first = ray(new double[]{0, 1.5, 0}, top);
		// Aimed 10 cm to the side of it.
		ViewRay second = ray(new double[]{5, 1.5, 0}, new double[]{0, 20, -14.9});

		CursorHit hit = RayCrossing.cross(first, second, null);

		assertArrayEquals(top, hit.point, 0.1);
	}

	@Test
	public void needsSightingsFarEnoughApart() {
		double[] top = {0, 25, -10};
		ViewRay first = ray(new double[]{0, 1.5, 0}, top);
		ViewRay close = ray(new double[]{0.3, 1.5, 0}, top);
		ViewRay apart = ray(new double[]{3, 1.5, 0}, top);
		ViewRay further = ray(new double[]{8, 1.5, 0}, top);

		assertNull(RayCrossing.cross(first, close, null));
		assertTrue(RayCrossing.cross(first, further, null).uncertaintyMeters
				< RayCrossing.cross(first, apart, null).uncertaintyMeters);
	}

	@Test
	public void needsSightingsCrossingAhead() {
		// Pointing away from each other.
		ViewRay first = new ViewRay(new double[]{0, 0, 0}, new double[]{-1, 0, -1});
		ViewRay second = new ViewRay(new double[]{4, 0, 0}, new double[]{1, 0, -1});

		assertNull(RayCrossing.cross(first, second, null));
	}

	@Test
	public void facesTheWallOrTheCamera() {
		double[] top = {0, 20, -10};
		ViewRay first = ray(new double[]{0, 1.5, 0}, top);
		ViewRay second = ray(new double[]{4, 1.5, 0}, top);
		double[] wall = {0, 0, 1};

		assertArrayEquals(wall, RayCrossing.cross(first, second, wall).normal, 1e-9);
		double[] facing = RayCrossing.cross(first, second, null).normal;
		assertTrue(angleDegrees(facing, CursorHit.subtract(second.origin, top)) < 1e-6);
	}

	@Test
	public void coloursTheCursorBySource() {
		assertArrayEquals(CursorRing.DEPTH_COLOR, CursorRing.colorOf(CursorHit.Source.DEPTH), 0);
		assertArrayEquals(CursorRing.WALL_PLANE_COLOR,
				CursorRing.colorOf(CursorHit.Source.WALL_PLANE), 0);
		assertArrayEquals(CursorRing.SIGHTING_COLOR,
				CursorRing.colorOf(CursorHit.Source.SIGHTING), 0);
	}
}
