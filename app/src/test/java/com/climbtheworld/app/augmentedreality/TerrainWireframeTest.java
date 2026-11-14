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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TerrainWireframeTest {
	private static final double LATITUDE = 45.52;
	private static final double LONGITUDE = -73.535;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	private static final Vector2d ANGLE_OF_VIEW = new Vector2d(60, 80);
	private static final Vector2d VIEW_SIZE = new Vector2d(1000, 1500);
	private static final Vector2d CONTAINER_SIZE = new Vector2d(2000, 2000);
	private static final double EYE_HEIGHT = 1.5;

	// For the tests of the samplings running off the main thread: a wireframe whose samplings
	// are held until the test runs them, over flat ground.
	private final HeldSamplings samplings = new HeldSamplings();
	// The ground elevation, and how far from the observer the terrain is loaded, as the
	// snapshots taken from now on have them.
	private double ground = 100;
	private double loadedWithin = Double.POSITIVE_INFINITY;
	private int snapshotsTaken;
	private int lookups;
	// Where each sampling was centred.
	private final List<Double> centreLatitudes = new ArrayList<>();
	private final int[] snapshotsApplied = {0};
	private int drawRequests;
	private final TerrainWireframe background = new TerrainWireframe(() -> {
		snapshotsTaken++;
		double snapshotGround = ground;
		double snapshotLoadedWithin = loadedWithin;
		return snapshot((latitude, longitude, distance) -> {
			lookups++;
			// Only the centre is read at no distance.
			if (distance == 0) {
				centreLatitudes.add(latitude);
			}
			return distance < snapshotLoadedWithin ? snapshotGround : Double.NaN;
		}, snapshotsApplied);
	}, samplings, () -> drawRequests++);

	private static double curvatureDrop(double distance) {
		return 0.87 * distance * distance / (2 * GeoUtils.EARTH_RADIUS_M);
	}

	private static void project(TerrainWireframe wireframe, double latitude, double eyeElevation,
	                            double azimuthDeg, double pitchDeg) {
		wireframe.project(latitude, LONGITUDE, eyeElevation, EYE_HEIGHT, azimuthDeg, pitchDeg,
				AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.x, VIEW_SIZE.x),
				AugmentedRealityUtils.focalLength(ANGLE_OF_VIEW.y, VIEW_SIZE.y),
				CONTAINER_SIZE.x / 2, CONTAINER_SIZE.y / 2);
	}

	/**
	 * A wireframe that samples on the spot, on the thread asking for it, for the tests of what
	 * it shows and of when it samples.
	 */
	private static TerrainWireframe sampledOnTheSpot(TerrainWireframe.ElevationSource source) {
		return new TerrainWireframe(() -> snapshot(source, null), (work, done) -> {
			work.run();
			done.run();
		}, () -> {
		});
	}

	/**
	 * @param applied counts the snapshots told that what was read of them is on display, or null
	 */
	private static TerrainWireframe.ElevationSnapshot snapshot(
			TerrainWireframe.ElevationSource source, int[] applied) {
		return new TerrainWireframe.ElevationSnapshot() {
			@Override
			public double getElevation(double latitude, double longitude, double distance) {
				return source.getElevation(latitude, longitude, distance);
			}

			@Override
			public void applied() {
				if (applied != null) {
					applied[0]++;
				}
			}
		};
	}

	private static int firstRingBeyond(double meters) {
		int ring = 0;
		while (RING_DISTANCES[ring] < meters) {
			ring++;
		}
		return ring;
	}

	@Test
	public void projectsLikeThePois() {
		// Ground rising with the distance, the same along every ray.
		TerrainWireframe wireframe =
				sampledOnTheSpot((latitude, longitude, distance) -> 100 + 0.3 * distance);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);
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
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) -> 100);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);
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
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) ->
				distance > 100 && distance < 130 ? 150 : 100);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);
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
	public void rayComesBackIntoViewOverTheRidge() {
		// A mountain far behind the ridge, rising above it.
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) ->
				distance > 100 && distance < 130 ? 150
						: distance > 1000 ? 100 + 2 * (distance - 1000) : 100);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);
		project(wireframe, LATITUDE, 101.5, 0, 0);

		int ridge = sampleIndex(0, firstRingBeyond(100));
		int ring = firstRingBeyond(130);
		while (!wireframe.isDrawable(sampleIndex(0, ring))) {
			ring++;
		}
		int mountain = sampleIndex(0, ring);
		assertTrue("Mountain seen past hidden terrain", RING_DISTANCES[ring] > 1000);
		assertTrue(wireframe.isEmerging(mountain));
		// The line to the mountain starts on the line of sight over the ridge, below the mountain.
		assertEquals(CONTAINER_SIZE.x / 2, wireframe.getEmergeX(mountain), 1e-3);
		assertEquals(wireframe.getScreenY(ridge), wireframe.getEmergeY(mountain), 0.05);
		assertTrue(wireframe.getEmergeY(mountain) > wireframe.getScreenY(mountain));
		assertFalse("Ridge seen from the start", wireframe.isEmerging(ridge));
	}

	@Test
	public void farTerrainStaysPutBetweenSamplings() {
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) -> 100);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);
		// Sixty centimeters north, which is not enough to sample the terrain again.
		double latitude = LATITUDE + 0.6 / METERS_PER_DEGREE;
		assertFalse(wireframe.update(latitude, LONGITUDE, 0, 0));
		project(wireframe, latitude, 101.5, 0, 0);

		int ring = firstRingBeyond(100);
		double distance = RING_DISTANCES[ring];
		Vector4d expected = AugmentedRealityUtils.getXYPosition(0,
				GeoUtils.calculateElevationAngle(101.5, 100 - curvatureDrop(distance),
						distance - 0.6), 0, 0, 0, new Vector2d(0, 0), ANGLE_OF_VIEW, VIEW_SIZE,
				CONTAINER_SIZE);
		assertEquals(expected.y, wireframe.getScreenY(sampleIndex(0, ring)), 0.05);
	}

	@Test
	public void reachesTheFeet() {
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) -> 100);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);
		// Looking down, the bottom of the view is about 1.3 meters ahead of the feet.
		project(wireframe, LATITUDE, 101.5, 0, -10);

		assertTrue(RING_DISTANCES[0] < 1);
		for (int ring = 0; RING_DISTANCES[ring] < 5; ring++) {
			assertTrue("Ring " + ring + " drawn", wireframe.isDrawable(sampleIndex(0, ring)));
		}
		assertTrue("Bottom of the view covered",
				wireframe.getScreenY(sampleIndex(0, 0)) > (CONTAINER_SIZE.y + VIEW_SIZE.y) / 2);
	}

	@Test
	public void feetStayUnderTheObserverBetweenSamplings() {
		// Falling southwards as steep as it rises, 45 degrees.
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) ->
				100 + (latitude - LATITUDE) * METERS_PER_DEGREE);
		wireframe.update(LATITUDE, LONGITUDE, 0, 0);

		// Facing downhill and looking down, nearly a meter behind the centre, where the rays
		// meet, then nearly a meter past it.
		int ray = RAY_COUNT / 2;
		for (double north : new double[]{0.95, -0.95}) {
			double latitude = LATITUDE + north / METERS_PER_DEGREE;
			assertFalse(wireframe.update(latitude, LONGITUDE, 0, 0));
			double eyeElevation = 100 + north + EYE_HEIGHT;
			project(wireframe, latitude, eyeElevation, 180, -45);

			int first = sampleIndex(ray, 0);
			assertTrue(north + ": first ring drawn", wireframe.isDrawable(first));
			// The ground 10 cm either side of the first ring, as seen from the observer.
			double[] screenY = new double[2];
			for (int side = 0; side < 2; side++) {
				double ahead = RING_DISTANCES[0] + (side == 0 ? 0.1 : -0.1);
				screenY[side] = AugmentedRealityUtils.getXYPosition(0,
						GeoUtils.calculateElevationAngle(eyeElevation,
								eyeElevation - EYE_HEIGHT - ahead, ahead), -45, 0, 0,
						new Vector2d(0, 0), ANGLE_OF_VIEW, VIEW_SIZE, CONTAINER_SIZE).y;
			}
			assertTrue(north + ": first ring about its distance ahead",
					wireframe.getScreenY(first) > screenY[0]
							&& wireframe.getScreenY(first) < screenY[1]);
			for (int ring = 0; RING_DISTANCES[ring] < 5; ring++) {
				assertTrue(north + ": ring " + ring + " ahead drawn",
						wireframe.isDrawable(sampleIndex(ray, ring)));
			}
		}
	}

	@Test
	public void samplesAgainWhenMovedOrOnceMoreTerrainLoaded() {
		int[] calls = {0};
		boolean[] loaded = {false};
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) -> {
			calls[0]++;
			return loaded[0] || distance < 1000 ? 100 : Double.NaN;
		});

		assertTrue("First sampling", wireframe.update(LATITUDE, LONGITUDE, 1, 0));
		// Every ring of every ray, and the centre.
		assertEquals((long) RAY_COUNT * RING_COUNT + 1, calls[0]);
		assertFalse("Still loading, with nothing more loaded",
				wireframe.update(LATITUDE, LONGITUDE, 1, 5000));

		// The far terrain loaded, which changes the terrain's version.
		loaded[0] = true;
		assertFalse("More loaded, but just sampled", wireframe.update(LATITUDE, LONGITUDE, 2,
				TerrainWireframe.TERRAIN_RESAMPLE_DELAY_MS - 1));
		assertTrue("More loaded", wireframe.update(LATITUDE, LONGITUDE, 2, 6000));
		assertFalse("Loaded", wireframe.update(LATITUDE, LONGITUDE, 2, 600_000));
		assertTrue("More detail loaded", wireframe.update(LATITUDE, LONGITUDE, 3, 600_000));
		assertTrue("Moved",
				wireframe.update(LATITUDE + 10 / METERS_PER_DEGREE, LONGITUDE, 3, 600_001));
	}

	@Test
	public void asksAgainNowAndThenWhileSomeTerrainIsMissing() {
		int[] calls = {0};
		// The far terrain never loads, as without a network.
		TerrainWireframe wireframe = sampledOnTheSpot((latitude, longitude, distance) -> {
			calls[0]++;
			return distance < 1000 ? 100 : Double.NaN;
		});
		assertTrue(wireframe.update(LATITUDE, LONGITUDE, 1, 0));
		int perSampling = calls[0];

		// A minute of frames, 50 a second.
		for (long millis = 20; millis <= 60_000; millis += 20) {
			wireframe.update(LATITUDE, LONGITUDE, 1, millis);
		}

		// Before, it was sampled again every second: 60 times over.
		assertEquals("Sampled again every " + TerrainWireframe.MISSING_RETRY_DELAY_MS + " ms",
				perSampling * (1 + 60_000 / TerrainWireframe.MISSING_RETRY_DELAY_MS),
				(long) calls[0]);
	}

	private static double north(double meters) {
		return LATITUDE + meters / METERS_PER_DEGREE;
	}

	/**
	 * @return where the ground 100 meters straight ahead is drawn, looking north on the level.
	 * Unlike the rings around the feet, it does not follow the ground under the observer.
	 */
	private float groundAheadScreenY() {
		int index = sampleIndex(0, firstRingBeyond(100));
		project(background, LATITUDE, 101.5, 0, 0);
		assertTrue(background.isDrawable(index));
		return background.getScreenY(index);
	}

	private void assertNothingDrawn() {
		project(background, LATITUDE, 101.5, 0, 0);
		for (int ray = 0; ray < RAY_COUNT; ray++) {
			assertEquals("Skyline of ray " + ray, -1, background.getSkyline(ray));
			for (int ring = 0; ring < RING_COUNT; ring++) {
				assertFalse("Ray " + ray + ", ring " + ring,
						background.isDrawable(sampleIndex(ray, ring)));
			}
		}
	}

	@Test
	public void nothingIsDrawnUntilTheFirstSamplingIsBack() {
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		assertFalse(background.isSampled());
		assertNothingDrawn();

		// Sampled, off the main thread, but not handed back to it yet.
		Runnable handBack = samplings.work();
		assertEquals((long) RAY_COUNT * RING_COUNT + 1, lookups);
		assertFalse(background.isSampled());
		assertNothingDrawn();
		assertEquals(0, drawRequests);

		handBack.run();
		assertTrue(background.isSampled());
		assertEquals("Drawn once sampled", 1, drawRequests);
		assertEquals("The tiles found missing are asked for", 1, snapshotsApplied[0]);
		assertTrue(groundAheadScreenY() > CONTAINER_SIZE.y / 2);
	}

	@Test
	public void theSamplesOnDisplayStayUntilTheNextSamplingIsBack() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		samplings.finish();
		float before = groundAheadScreenY();

		// More detail loaded, which has the ground ahead 50 meters lower.
		ground = 50;
		assertTrue(background.update(LATITUDE, LONGITUDE, 2, 5000));
		assertEquals(before, groundAheadScreenY(), 0);
		Runnable handBack = samplings.work();
		assertEquals("Not half written either", before, groundAheadScreenY(), 0);

		handBack.run();
		assertTrue("Further below the horizon", groundAheadScreenY() > before + 100);
		assertEquals(2, drawRequests);
	}

	@Test
	public void anUpdateDuringASamplingWaitsForItThenIsSampled() {
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		// The observer walks on while it runs, a frame at a time, 5 meters in all.
		for (int frame = 1; frame <= 50; frame++) {
			assertFalse(background.update(north(0.1 * frame), LONGITUDE, 1, 20 * frame));
		}
		assertEquals("One sampling at a time, and none queued", 1, samplings.count());
		assertEquals(1, snapshotsTaken);

		samplings.finish();
		// The first sampling is on display, and the next one is under way with no further
		// update, around where the observer was as of the last one.
		assertTrue(background.isSampled());
		assertEquals(1, samplings.count());
		assertEquals(2, snapshotsTaken);

		samplings.finish();
		assertEquals(0, samplings.count());
		assertEquals(2, centreLatitudes.size());
		assertEquals(LATITUDE, centreLatitudes.get(0), 0);
		assertEquals(north(5), centreLatitudes.get(1), 0);
		assertFalse(background.update(north(5), LONGITUDE, 1, 2000));
	}

	@Test
	public void anUpdateDuringASamplingThatCallsForNoneStartsNone() {
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		// Half a meter is not enough to sample again.
		assertFalse(background.update(north(0.5), LONGITUDE, 1, 20));

		samplings.finish();
		assertEquals(0, samplings.count());
		assertEquals(1, snapshotsTaken);
	}

	@Test
	public void aSnapshotIsOnlyTakenForASampling() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		samplings.finish();

		// A minute of frames, 50 a second.
		for (long millis = 20; millis <= 60_000; millis += 20) {
			assertFalse(background.update(LATITUDE, LONGITUDE, 1, millis));
		}
		assertEquals(1, snapshotsTaken);
		assertEquals(1, drawRequests);
	}

	@Test
	public void aSamplingBackAfterTheScreenIsDestroyedIsDropped() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		background.destroy();

		Runnable handBack = samplings.work();
		assertEquals("Given up on: it stops short, after the centre", 1, lookups);
		handBack.run();
		assertFalse(background.isSampled());
		assertEquals("The terrain was given back: nothing more is asked of it", 0,
				snapshotsApplied[0]);
		assertEquals(0, drawRequests);

		assertFalse(background.update(LATITUDE, LONGITUDE, 1, 20));
		assertEquals(0, samplings.count());
		assertEquals(1, snapshotsTaken);
	}

	@Test
	public void aSamplingDoneBeforeTheScreenIsDestroyedIsDroppedToo() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		Runnable handBack = samplings.work();
		background.destroy();

		handBack.run();
		assertFalse(background.isSampled());
		assertEquals(0, snapshotsApplied[0]);
		assertEquals(0, drawRequests);
	}

	@Test
	public void aSamplingBackAfterTheWireframeIsHiddenIsDropped() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		Runnable handBack = samplings.work();
		background.hide();

		handBack.run();
		assertFalse(background.isSampled());
		assertEquals(0, snapshotsApplied[0]);
		assertEquals(0, drawRequests);
		assertEquals("Nothing is sampled while hidden", 0, samplings.count());

		// Shown again, it is sampled as at first.
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 100));
		samplings.finish();
		assertTrue(background.isSampled());
	}

	@Test
	public void aWireframeShownAgainDuringItsSamplingIsSampledOnceThatIsBack() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		background.hide();
		// The sampling given up on still runs: the next one waits for it.
		assertFalse(background.update(LATITUDE, LONGITUDE, 1, 40));
		assertEquals(1, samplings.count());

		samplings.finish();
		assertEquals("Stopped short", 1, lookups);
		assertFalse("Dropped", background.isSampled());
		assertEquals(1, samplings.count());

		samplings.finish();
		assertTrue(background.isSampled());
		assertEquals(1, snapshotsApplied[0]);
		assertTrue(groundAheadScreenY() > CONTAINER_SIZE.y / 2);
	}

	@Test
	public void terrainLoadedDuringASamplingIsSampledOnceItsDelayIsOver() {
		loadedWithin = 1000;
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		// The far terrain loads while it runs, which changes the terrain's version.
		loadedWithin = Double.POSITIVE_INFINITY;
		assertFalse(background.update(LATITUDE, LONGITUDE, 2, 500));

		samplings.finish();
		// On display as it was read, from before the far terrain loaded.
		project(background, LATITUDE, 101.5, 0, 0);
		assertFalse(background.isDrawable(sampleIndex(0, RING_COUNT - 1)));
		assertEquals("Sampled half a second ago", 0, samplings.count());
		assertFalse(background.update(LATITUDE, LONGITUDE, 2,
				TerrainWireframe.TERRAIN_RESAMPLE_DELAY_MS - 1));

		assertTrue(background.update(LATITUDE, LONGITUDE, 2,
				TerrainWireframe.TERRAIN_RESAMPLE_DELAY_MS));
		samplings.finish();
		project(background, LATITUDE, 101.5, 0, 0);
		assertTrue(background.isDrawable(sampleIndex(0, RING_COUNT - 1)));
		assertFalse("Loaded", background.update(LATITUDE, LONGITUDE, 2, 600_000));
	}

	@Test
	public void terrainLoadedDuringASlowSamplingIsSampledAsSoonAsThatIsBack() {
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		assertFalse(background.update(LATITUDE, LONGITUDE, 2,
				TerrainWireframe.TERRAIN_RESAMPLE_DELAY_MS + 500));

		samplings.finish();
		assertEquals("With no further update", 1, samplings.count());
		samplings.finish();
		assertEquals(0, samplings.count());
		assertFalse(background.update(LATITUDE, LONGITUDE, 2, 600_000));
	}

	@Test
	public void missingTerrainIsAskedForAgainNowAndThenOneSamplingAtATime() {
		// The far terrain never loads, as without a network.
		loadedWithin = 1000;
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		samplings.finish();
		assertEquals(1, snapshotsApplied[0]);

		for (long millis = 20; millis < TerrainWireframe.MISSING_RETRY_DELAY_MS; millis += 20) {
			assertFalse(background.update(LATITUDE, LONGITUDE, 1, millis));
		}
		assertTrue(background.update(LATITUDE, LONGITUDE, 1,
				TerrainWireframe.MISSING_RETRY_DELAY_MS));
		// It runs for a second, during which the frames start no other.
		for (long millis = 20; millis <= 1000; millis += 20) {
			assertFalse(background.update(LATITUDE, LONGITUDE, 1,
					TerrainWireframe.MISSING_RETRY_DELAY_MS + millis));
		}
		assertEquals(1, samplings.count());

		samplings.finish();
		// Asked for again by the sampling, and not before the next delay is over, which runs
		// from when the sampling started.
		assertEquals(2, snapshotsApplied[0]);
		assertEquals(0, samplings.count());
		assertFalse(background.update(LATITUDE, LONGITUDE, 1,
				2 * TerrainWireframe.MISSING_RETRY_DELAY_MS - 1));
		assertTrue(background.update(LATITUDE, LONGITUDE, 1,
				2 * TerrainWireframe.MISSING_RETRY_DELAY_MS));
	}

	@Test
	public void samplesTooFarFromTheObserverAreNotDrawnForThem() {
		background.update(LATITUDE, LONGITUDE, 1, 0);
		samplings.finish();

		// A few meters on, as between two samplings when moving fast, they stay on display.
		assertTrue(background.update(north(5), LONGITUDE, 1, 1000));
		assertTrue(background.isSampled());
		samplings.finish();

		// The observer moved on while the view was hidden or paused: the samples from back
		// there go, and nothing is drawn until the terrain is sampled around them.
		assertTrue(background.update(north(55), LONGITUDE, 1, 60_000));
		assertFalse(background.isSampled());
		assertNothingDrawn();
		samplings.finish();
		assertTrue(background.isSampled());
		assertEquals(north(55), centreLatitudes.get(2), 0);
	}

	@Test
	public void aSamplingBackFromTooFarIsNotDrawnEither() {
		assertTrue(background.update(LATITUDE, LONGITUDE, 1, 0));
		// The observer is 50 meters on by the time it is back.
		assertFalse(background.update(north(50), LONGITUDE, 1, 20));
		samplings.finish();
		assertEquals("Sampled around the observer next", 1, samplings.count());

		// The next frame: nothing shows until that one is back.
		assertFalse(background.update(north(50), LONGITUDE, 1, 40));
		assertFalse(background.isSampled());
		assertNothingDrawn();
		samplings.finish();
		assertTrue(background.isSampled());
		assertEquals(0, samplings.count());
	}

	/** Holds the samplings handed to it, for a test to run each when it chooses. */
	private static final class HeldSamplings implements TerrainWireframe.Sampler {
		private final Deque<Runnable[]> held = new ArrayDeque<>();

		@Override
		public void run(Runnable work, Runnable done) {
			held.add(new Runnable[]{work, done});
		}

		int count() {
			return held.size();
		}

		/**
		 * Runs the work of the oldest sampling held, as off the main thread.
		 *
		 * @return what hands the sampling back, to be run as on the main thread
		 */
		Runnable work() {
			Runnable[] sampling = held.remove();
			sampling[0].run();
			return sampling[1];
		}

		/** Runs the oldest sampling held, and hands it back. */
		void finish() {
			work().run();
		}
	}
}
