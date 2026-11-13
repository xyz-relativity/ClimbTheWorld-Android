package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReloadGuardTest {
	private static final double VIEW_DISTANCE = 2000;
	// A quarter of the view distance is 500 m.
	private static final double SHARE = 0.25;
	private static final double LATITUDE = 45;
	private static final double LONGITUDE = 24;
	// About 111 km to a degree of latitude.
	private static final double NORTH_1_METER = 1 / 111_000.0;
	private static final double NORTH_400_METERS = 400 / 111_000.0;
	private static final double NORTH_600_METERS = 600 / 111_000.0;

	private final ReloadGuard guard = ReloadGuard.afterShare(SHARE);

	@Test
	public void theFirstRequestLoads() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void nothingLoadsAgainWhileTheObserverStaysClose() {
		load(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		assertFalse(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		assertFalse(guard.request(LATITUDE + NORTH_400_METERS, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void itLoadsAgainOnceTheObserverMovedTheShareOfTheViewDistance() {
		load(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		assertTrue(guard.request(LATITUDE + NORTH_600_METERS, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void theMoveIsMeasuredFromWhereItWasLoadedNotFromTheLastRequest() {
		load(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		assertFalse(guard.request(LATITUDE + NORTH_400_METERS, LONGITUDE, VIEW_DISTANCE));
		// 200 m from the last request, but 600 m from where it was loaded.
		assertTrue(guard.request(LATITUDE + NORTH_600_METERS, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void aMoveEastCountsInMetersNotDegrees() {
		load(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		// At 45 degrees north, the 600 m north in degrees are only about 420 m east.
		assertFalse(guard.request(LATITUDE, LONGITUDE + NORTH_600_METERS, VIEW_DISTANCE));
		assertTrue(guard.request(LATITUDE, LONGITUDE + 2 * NORTH_600_METERS, VIEW_DISTANCE));
	}

	@Test
	public void aGuardInMetersLoadsAgainAfterThemWhateverTheViewDistance() {
		ReloadGuard afterTwoMeters = ReloadGuard.afterMeters(2);
		assertTrue(afterTwoMeters.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		afterTwoMeters.loaded(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		assertFalse(afterTwoMeters.request(LATITUDE + NORTH_1_METER, LONGITUDE, VIEW_DISTANCE));
		assertTrue(afterTwoMeters.request(LATITUDE + 3 * NORTH_1_METER, LONGITUDE,
				VIEW_DISTANCE));
	}

	@Test
	public void itLoadsAgainForAnotherViewDistance() {
		load(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE * 2));
	}

	@Test
	public void itLoadsAgainOnceTheDataChanged() {
		load(LATITUDE, LONGITUDE, VIEW_DISTANCE);

		guard.dataChanged();

		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void onlyOneLoadRunsAtATime() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		assertFalse(guard.request(LATITUDE + NORTH_600_METERS, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void aRequestMadeDuringALoadIsToBeMadeAgainAfterIt() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		assertFalse(guard.request(LATITUDE + NORTH_600_METERS, LONGITUDE, VIEW_DISTANCE));

		assertTrue(guard.loaded(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		assertTrue(guard.request(LATITUDE + NORTH_600_METERS, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void aLoadNoRequestWaitedForAsksForNoOther() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));

		assertFalse(guard.loaded(LATITUDE, LONGITUDE, VIEW_DISTANCE));
	}

	@Test
	public void dataChangedDuringALoadIsLoadedAgainAfterIt() {
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		guard.dataChanged();

		assertTrue(guard.loaded(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		assertTrue(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		// That load read the changed data.
		assertFalse(guard.loaded(LATITUDE, LONGITUDE, VIEW_DISTANCE));
		assertFalse(guard.request(LATITUDE, LONGITUDE, VIEW_DISTANCE));
	}

	private void load(double latitude, double longitude, double viewDistance) {
		assertTrue(guard.request(latitude, longitude, viewDistance));
		guard.loaded(latitude, longitude, viewDistance);
	}
}
