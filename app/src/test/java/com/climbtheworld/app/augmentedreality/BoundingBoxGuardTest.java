package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoundingBoxGuardTest {
	private static final double VIEW_DISTANCE = 2500;
	private static final double LATITUDE = 45;
	private static final double LONGITUDE = 24;
	// About 111 km to a degree of latitude.
	private static final double NORTH_1_METER = 1 / 111_000.0;

	private final BoundingBoxGuard guard = new BoundingBoxGuard();

	@Test
	public void theFirstRequestPicksThePois() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void theyAreNotPickedAgainWhileTheCameraStaysClose() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		assertFalse(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		// A frame of ARCore's camera on: a few centimeters.
		assertFalse(guard.request(LATITUDE + 0.03 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
		assertFalse(guard.request(LATITUDE - 0.9 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void theyArePickedAgainOnceTheCameraMovedAMeter() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		assertTrue(guard.request(LATITUDE + 1.1 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
		// From there on.
		assertFalse(guard.request(LATITUDE + 1.5 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
		assertTrue(guard.request(LATITUDE + 2.2 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void theMoveIsMeasuredFromWhereTheyWerePickedNotFromTheLastRequest() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		// Small steps, as the frames of a walk, or of a GPS move's animation, add up.
		assertFalse(guard.request(LATITUDE + 0.4 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
		assertFalse(guard.request(LATITUDE + 0.8 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
		assertTrue(guard.request(LATITUDE + 1.2 * NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void aMoveEastCountsInMetersNotDegrees() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		// At 45 degrees north, 1.2 m north in degrees are only about 0.85 m east.
		assertFalse(guard.request(LATITUDE, LONGITUDE + 1.2 * NORTH_1_METER, VIEW_DISTANCE));
		assertTrue(guard.request(LATITUDE, LONGITUDE + 2.4 * NORTH_1_METER, VIEW_DISTANCE));
	}

	@Test
	public void theyArePickedAgainForAnotherViewDistance() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE - 500));
		assertFalse(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE - 500));
	}

	@Test
	public void theyArePickedAgainOnceThePoisLoadedChanged() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		guard.poisChanged();

		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		// Once for the change.
		assertFalse(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void aCameraNowhereYetHasThemPickedEveryTime() {
		// As the box would be worked out around nowhere, it holds nothing to keep.
		assertTrue(guard.request(Double.NaN, Double.NaN, VIEW_DISTANCE));
		assertTrue(guard.request(Double.NaN, Double.NaN, VIEW_DISTANCE));
	}
}
