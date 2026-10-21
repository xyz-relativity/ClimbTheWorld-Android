package com.climbtheworld.app.augmentedreality.placement;

import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.CENTRE_X;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.CENTRE_Y;
import static com.climbtheworld.app.augmentedreality.placement.DepthTestFrames.HEIGHT;
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
 * DepthFrame, DepthWireframe, CursorHit and CursorRing.
 */
public class DepthPlacementTest {
	private static final double MILLIMETER = 0.001;
	// The net's spacing close by, and far away.
	private static final double CLOSE_SPACING = DepthWireframe.SPACINGS_METERS[0];
	private static final double FAR_SPACING =
			DepthWireframe.SPACINGS_METERS[DepthWireframe.SPACINGS_METERS.length - 1];

	private static double angleDegrees(double[] a, double[] b) {
		return Math.toDegrees(Math.acos(Math.min(1, CursorHit.dot(a, b)
				/ (CursorHit.length(a) * CursorHit.length(b)))));
	}

	@Test
	public void unprojectsThroughTheScaledTextureIntrinsics() {
		DepthFrame frame = frame(flat(10), cameraAt(0, 0, 0));
		double[] point = new double[3];

		frame.toWorld(CENTRE_X, CENTRE_Y, 10, point);
		assertArrayEquals(new double[]{0, 0, -10}, point, 1e-9);

		// Right and down in the image are right and down in front of the camera.
		frame.toWorld(CENTRE_X + 12, CENTRE_Y + 24, 10, point);
		assertArrayEquals(new double[]{1, -2, -10}, point, 1e-9);
	}

	@Test
	public void movesPointsWithTheCamera() {
		DepthFrame frame = frame(flat(10), cameraAt(5, 1.5f, -2));
		double[] point = new double[3];
		frame.toWorld(CENTRE_X, CENTRE_Y, 10, point);

		assertArrayEquals(new double[]{5, 1.5, -12}, point, 1e-6);
		assertArrayEquals(new double[]{5, 1.5, -2}, frame.cameraPosition(), 1e-6);
	}

	@Test
	public void leavesOutUnknownAndFarDepths() {
		short[] depth = flat(10);
		depth[0] = 0;
		depth[1] = (short) Math.round((DepthFrame.MAX_DEPTH_METERS + 1) * 1000);
		DepthFrame frame = frame(depth, cameraAt(0, 0, 0));

		assertTrue(Double.isNaN(frame.depthMeters(0, 0)));
		assertTrue(Double.isNaN(frame.depthMeters(1, 0)));
		assertEquals(10, frame.depthMeters(2, 0), MILLIMETER);
	}

	@Test
	public void readsDepthsPast32MetersAsFar() {
		// Past 32.767 m, the millimeters no longer fit a signed short.
		DepthFrame frame = frame(flat(40), cameraAt(0, 0, 0));

		assertTrue(Double.isNaN(frame.depthMeters(5, 5)));
	}

	/**
	 * The lines' ends, as {x, y, z} pairs.
	 */
	private static double[][][] lines(LineVertices vertices) {
		float[] floats = vertices.getFloats();
		double[][][] lines = new double[vertices.getCount() / 2][2][];
		for (int line = 0; line < lines.length; line++) {
			for (int end = 0; end < 2; end++) {
				int start = (2 * line + end) * LineVertices.FLOATS_PER_VERTEX;
				lines[line][end] = new double[]{floats[start], floats[start + 1], floats[start + 2]};
			}
		}
		return lines;
	}

	private static boolean onMultiple(double value, double spacing) {
		double steps = value / spacing;
		return Math.abs(steps - Math.round(steps)) < 1e-3;
	}

	/**
	 * Whether a line runs along a plane of the net: the given coordinate is the same at both ends,
	 * a multiple of the spacing.
	 */
	private static boolean alongPlane(double[][] line, int coordinate, double spacing) {
		return Math.abs(line[0][coordinate] - line[1][coordinate]) < 1e-3
				&& onMultiple(line[0][coordinate], spacing);
	}

	private static double[][][] netOn(short[] depth, float[] camera) {
		LineVertices vertices = new LineVertices();
		DepthWireframe wireframe = new DepthWireframe();
		wireframe.setFacing(0);
		wireframe.build(frame(depth, camera), vertices);
		return lines(vertices);
	}

	@Test
	public void drapesASquareNetOnTheRockAhead() {
		// Facing the session's north, -Z: upright lines at even x, level ones at even y.
		double[][][] lines = netOn(flat(8), cameraAt(0, 0, 0));

		int upright = 0;
		int level = 0;
		for (double[][] line : lines) {
			if (alongPlane(line, 0, CLOSE_SPACING)) {
				upright++;
			} else if (alongPlane(line, 1, CLOSE_SPACING)) {
				level++;
			} else {
				throw new AssertionError("Off the net: " + java.util.Arrays.deepToString(line));
			}
		}
		assertTrue(upright > 0);
		assertTrue(level > 0);
	}

	@Test
	public void keepsTheNetInPlaceWhenThePhoneMoves() {
		// The same wall, 8 m away, from 30 cm to the right and 20 cm higher.
		for (double[][] line : netOn(flat(8), cameraAt(0.3f, 0.2f, 0))) {
			assertTrue(alongPlane(line, 0, CLOSE_SPACING) || alongPlane(line, 1, CLOSE_SPACING));
		}
	}

	@Test
	public void drapesTheNetOnTheGroundToo() {
		DepthFrame ground = frame(plane(new double[]{0, -1.37, 0}, new double[]{0, 1, 0}),
				cameraAt(0, 0, 0));
		LineVertices vertices = new LineVertices();
		DepthWireframe wireframe = new DepthWireframe();
		wireframe.setFacing(0);
		wireframe.build(ground, vertices);

		double[][][] lines = lines(vertices);
		assertTrue(lines.length > 0);
		for (double[][] line : lines) {
			assertTrue(alongPlane(line, 0, CLOSE_SPACING) || alongPlane(line, 2, CLOSE_SPACING));
		}
	}

	@Test
	public void spacesTheNetWiderFarAway() {
		for (double[][] line : netOn(flat(25), cameraAt(0, 0, 0))) {
			assertTrue(alongPlane(line, 0, FAR_SPACING) || alongPlane(line, 1, FAR_SPACING));
		}
	}

	@Test
	public void turnsTheNetWithTheWayTheUserFaces() {
		LineVertices vertices = new LineVertices();
		DepthWireframe wireframe = new DepthWireframe();
		// Facing the session's east, +X: a wall across it has upright lines at even z.
		wireframe.setFacing(90);
		float[] facingEast = {0, 0, 1, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 0, 1};
		wireframe.build(frame(flat(8), facingEast), vertices);

		for (double[][] line : lines(vertices)) {
			assertTrue(alongPlane(line, 2, CLOSE_SPACING) || alongPlane(line, 1, CLOSE_SPACING));
		}
	}

	@Test
	public void doesNotJoinAcrossAGap() {
		short[] depth = flat(10);
		// A tree in front of the left half of the rock.
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH / 2; x++) {
				depth[y * WIDTH + x] = 4000;
			}
		}

		for (double[][] line : netOn(depth, cameraAt(0, 0, 0))) {
			assertEquals(line[0][2], line[1][2], 1e-3);
		}
	}

	@Test
	public void fadesTheLinesWithTheDistance() {
		LineVertices near = new LineVertices();
		new DepthWireframe().build(frame(flat(DepthWireframe.NEAR_METERS), cameraAt(0, 0, 0)),
				near);
		LineVertices far = new LineVertices();
		new DepthWireframe().build(frame(flat(DepthFrame.MAX_DEPTH_METERS), cameraAt(0, 0, 0)),
				far);

		assertEquals(DepthWireframe.NEAR_COLOR[3], near.getFloats()[6], 1e-3);
		assertEquals(DepthWireframe.FAR_COLOR[3], far.getFloats()[6], 1e-3);
	}

	@Test
	public void hitsAWallSquareOn() {
		CursorHit hit = CursorHit.find(frame(flat(10), cameraAt(0, 0, 0)), CENTRE_X, CENTRE_Y);

		assertArrayEquals(new double[]{0, 0, -10}, hit.point, MILLIMETER);
		assertArrayEquals(new double[]{0, 0, 1}, hit.normal, 1e-6);
		assertEquals(10, hit.distance, MILLIMETER);
	}

	@Test
	public void findsWhichWayASlabFaces() {
		// Leaning back 30 degrees from vertical, and turned 20 degrees to the right.
		double lean = Math.toRadians(30);
		double turn = Math.toRadians(20);
		double[] normal = {Math.sin(turn) * Math.cos(lean), Math.sin(lean),
				Math.cos(turn) * Math.cos(lean)};
		DepthFrame frame = frame(plane(new double[]{0, 0, -8}, normal), cameraAt(0, 0, 0));

		CursorHit hit = CursorHit.find(frame, CENTRE_X, CENTRE_Y);

		assertArrayEquals(new double[]{0, 0, -8}, hit.point, 0.01);
		assertTrue(angleDegrees(normal, hit.normal) < 2);
	}

	@Test
	public void facesTheCameraWhereTheSlopeIsUnknown() {
		short[] depth = new short[WIDTH * HEIGHT];
		int centre = (int) CENTRE_Y * WIDTH + (int) CENTRE_X;
		depth[centre] = 6000;
		CursorHit hit = CursorHit.find(frame(depth, cameraAt(0, 0, 0)), CENTRE_X, CENTRE_Y);

		assertArrayEquals(new double[]{0, 0, 1}, hit.normal, 1e-6);
	}

	@Test
	public void missesWhereTheDepthIsUnknown() {
		assertNull(CursorHit.find(frame(new short[WIDTH * HEIGHT], cameraAt(0, 0, 0)),
				CENTRE_X, CENTRE_Y));
	}

	@Test
	public void laysTheRingOnTheRock() {
		double[] normal = {0.6, 0, 0.8};
		CursorHit hit = new CursorHit(new double[]{1, 2, -10}, normal, 10);
		LineVertices lines = new LineVertices();
		CursorRing.build(hit, lines);

		double radius = CursorRing.radius(10);
		float[] floats = lines.getFloats();
		assertEquals(2 * (CursorRing.SEGMENTS + 1), lines.getCount());
		for (int vertex = 0; vertex < 2 * CursorRing.SEGMENTS; vertex++) {
			int start = vertex * LineVertices.FLOATS_PER_VERTEX;
			double[] offset = {floats[start] - 1, floats[start + 1] - 2, floats[start + 2] + 10};
			assertEquals(radius, CursorHit.length(offset), MILLIMETER);
			assertEquals(0, CursorHit.dot(offset, normal), MILLIMETER);
		}
		int tip = (2 * CursorRing.SEGMENTS + 1) * LineVertices.FLOATS_PER_VERTEX;
		assertArrayEquals(new float[]{(float) (1 + 0.6 * radius), 2, (float) (-10 + 0.8 * radius)},
				new float[]{floats[tip], floats[tip + 1], floats[tip + 2]}, 1e-4f);
	}

	@Test
	public void growsTheRingWithTheDistance() {
		assertEquals(CursorRing.MIN_RADIUS_METERS, CursorRing.radius(0.5), 1e-9);
		assertEquals(2 * CursorRing.radius(10), CursorRing.radius(20), 1e-9);
	}

	@Test
	public void laysTheRingOnFlatGround() {
		CursorHit hit = new CursorHit(new double[]{0, -1.5, -3}, new double[]{0, 1, 0}, 3.4);
		LineVertices lines = new LineVertices();
		CursorRing.build(hit, lines);

		float[] floats = lines.getFloats();
		for (int vertex = 0; vertex < 2 * CursorRing.SEGMENTS; vertex++) {
			assertEquals(-1.5, floats[vertex * LineVertices.FLOATS_PER_VERTEX + 1], 1e-5);
		}
	}
}
