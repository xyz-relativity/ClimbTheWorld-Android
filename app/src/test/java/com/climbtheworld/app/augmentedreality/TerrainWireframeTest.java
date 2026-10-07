package com.climbtheworld.app.augmentedreality;

import static com.climbtheworld.app.augmentedreality.TerrainWireframe.RAY_COUNT;
import static com.climbtheworld.app.augmentedreality.TerrainWireframe.RING_COUNT;
import static com.climbtheworld.app.augmentedreality.TerrainWireframe.RING_DISTANCES;
import static com.climbtheworld.app.augmentedreality.TerrainWireframe.sampleIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;

import org.junit.Test;

public class TerrainWireframeTest {
	private static final double LATITUDE = 45.52;
	private static final double LONGITUDE = -73.535;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	private static final Vector2d ANGLE_OF_VIEW = new Vector2d(60, 80);
	private static final Vector2d VIEW_SIZE = new Vector2d(1000, 1500);
	private static final Vector2d CONTAINER_SIZE = new Vector2d(2000, 2000);

	private static double curvatureDrop(double distance) {
		return 0.87 * distance * distance / (2 * GeoUtils.EARTH_RADIUS_M);
	}

	private static void project(TerrainWireframe wireframe, double latitude, double eyeElevation,
	                            double azimuthDeg, double pitchDeg) {
		wireframe.project(latitude, LONGITUDE, eyeElevation, azimuthDeg, pitchDeg,
				AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.x, VIEW_SIZE.x),
				AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.y, VIEW_SIZE.y),
				CONTAINER_SIZE.x / 2, CONTAINER_SIZE.y / 2);
	}

	@Test
	public void projectsLikeThePois() {
		// Ground rising with the distance, the same along every ray.
		TerrainWireframe wireframe =
				new TerrainWireframe((latitude, longitude, distance) -> 100 + 0.3 * distance);
		wireframe.update(LATITUDE, LONGITUDE, 0);
		double eyeElevation = 101.5;

		double[][] cameras = {{0, 0}, {37, 10}, {200, -25}};
		for (double[] camera : cameras) {
			project(wireframe, LATITUDE, eyeElevation, camera[0], camera[1]);
			for (int ray = 0; ray < RAY_COUNT; ray += 9) {
				for (int ring = 0; ring < RING_COUNT; ring += 5) {
					double azimuth = 360.0 * ray / RAY_COUNT;
					double distance = RING_DISTANCES[ring];
					double elevationAngle = GeoUtils.calculateElevationAngle(eyeElevation,
							100 + 0.3 * distance - curvatureDrop(distance), distance);
					double yaw = GeoUtils.diffAngle(azimuth, camera[0]);
					int index = sampleIndex(ray, ring);
					if (AugmentedRealityUtils.angleFromCameraAxis(yaw, elevationAngle, camera[1])
							> 60) {
						continue;
					}

					assertTrue("Ray " + ray + ", ring " + ring + " drawn",
							wireframe.isDrawable(index));
					Vector4d expected = AugmentedRealityUtils.getXYPosition(yaw, elevationAngle,
							camera[1], 0, 0, new Vector2d(0, 0), ANGLE_OF_VIEW, VIEW_SIZE,
							CONTAINER_SIZE);
					assertEquals("x of ray " + ray + ", ring " + ring, expected.x,
							wireframe.getScreenX(index), 0.05);
					assertEquals("y of ray " + ray + ", ring " + ring, expected.y,
							wireframe.getScreenY(index), 0.05);
				}
			}
		}
	}

	@Test
	public void flatGroundIsBelowTheHorizonUpToTheSkyline() {
		TerrainWireframe wireframe = new TerrainWireframe((latitude, longitude, distance) -> 100);
		wireframe.update(LATITUDE, LONGITUDE, 0);
		project(wireframe, LATITUDE, 101.5, 0, 0);

		double previousY = Double.POSITIVE_INFINITY;
		for (int ring = 0; ring < RING_COUNT; ring++) {
			int index = sampleIndex(0, ring);
			assertTrue("Ring " + ring + " drawn", wireframe.isDrawable(index));
			assertEquals("Straight ahead", CONTAINER_SIZE.x / 2, wireframe.getScreenX(index),
					1e-3);
			assertTrue("Below the horizon", wireframe.getScreenY(index) > CONTAINER_SIZE.y / 2);
			assertTrue("Rising towards the horizon", wireframe.getScreenY(index) < previousY);
			previousY = wireframe.getScreenY(index);
		}
		// Within the radius, the curvature of the Earth does not yet outdo the perspective.
		assertEquals(sampleIndex(0, RING_COUNT - 1), wireframe.getSkyline(0));
		assertFalse("Behind the camera", wireframe.isDrawable(sampleIndex(RAY_COUNT / 2, 0)));
	}

	@Test
	public void ridgeHidesTheGroundBehindIt() {
		TerrainWireframe wireframe = new TerrainWireframe((latitude, longitude, distance) ->
				distance > 100 && distance < 130 ? 150 : 100);
		wireframe.update(LATITUDE, LONGITUDE, 0);
		project(wireframe, LATITUDE, 101.5, 0, 0);

		int firstRidgeRing = -1;
		for (int ring = 0; ring < RING_COUNT; ring++) {
			double distance = RING_DISTANCES[ring];
			boolean drawable = wireframe.isDrawable(sampleIndex(0, ring));
			if (distance <= 100) {
				assertTrue("In front of the ridge, ring " + ring, drawable);
			} else if (distance < 130) {
				if (firstRidgeRing < 0) {
					firstRidgeRing = ring;
					assertTrue("Ridge edge drawn", drawable);
				}
			} else {
				assertFalse("Behind the ridge, ring " + ring, drawable);
			}
		}
		// The flat top of the ridge is seen at its highest from its near edge.
		assertEquals(sampleIndex(0, firstRidgeRing), wireframe.getSkyline(0));
	}

	@Test
	public void followsTheObserverBetweenSamplings() {
		TerrainWireframe wireframe = new TerrainWireframe((latitude, longitude, distance) -> 100);
		wireframe.update(LATITUDE, LONGITUDE, 0);
		// Sixty centimeters north, which is not enough to sample the terrain again.
		double latitude = LATITUDE + 0.6 / METERS_PER_DEGREE;
		assertFalse(wireframe.update(latitude, LONGITUDE, 0));
		project(wireframe, latitude, 101.5, 0, 0);

		double distance = RING_DISTANCES[0];
		Vector4d expected = AugmentedRealityUtils.getXYPosition(0,
				GeoUtils.calculateElevationAngle(101.5, 100 - curvatureDrop(distance),
						distance - 0.6), 0, 0, 0, new Vector2d(0, 0), ANGLE_OF_VIEW, VIEW_SIZE,
				CONTAINER_SIZE);
		assertEquals(expected.y, wireframe.getScreenY(sampleIndex(0, 0)), 0.05);
	}

	@Test
	public void samplesAgainWhenMovedOrWhileLoading() {
		int[] calls = {0};
		boolean[] loaded = {false};
		TerrainWireframe wireframe = new TerrainWireframe((latitude, longitude, distance) -> {
			calls[0]++;
			return loaded[0] || distance < 1000 ? 100 : Double.NaN;
		});

		assertTrue("First sampling", wireframe.update(LATITUDE, LONGITUDE, 0));
		assertEquals(RAY_COUNT * RING_COUNT, calls[0]);
		assertFalse("Still loading, but just sampled", wireframe.update(LATITUDE, LONGITUDE, 500));
		assertTrue("Still loading", wireframe.update(LATITUDE, LONGITUDE, 1000));

		loaded[0] = true;
		assertTrue(wireframe.update(LATITUDE, LONGITUDE, 2000));
		assertFalse("Loaded", wireframe.update(LATITUDE, LONGITUDE, 5000));
		assertTrue("Moved", wireframe.update(LATITUDE + 10 / METERS_PER_DEGREE, LONGITUDE, 5001));
	}
}
