package com.climbtheworld.app.sensors.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationFixChooserTest {
	private static final long SECOND_NANOS = 1_000_000_000L;
	private static final String GPS = "gps";
	private static final String FUSED = "fused";
	private static final String NETWORK = "network";

	@Test
	public void nextFixFromTheSameProviderReplacesTheCurrentOne() {
		assertTrue(LocationFixChooser.shouldReplace(GPS, 4, 0, GPS, 25, SECOND_NANOS));
	}

	@Test
	public void olderFixFromTheSameProviderIsIgnored() {
		assertFalse(LocationFixChooser.shouldReplace(GPS, 4, SECOND_NANOS, GPS, 3, 0));
	}

	@Test
	public void providersAboutAsAccurateDoNotTakeTurns() {
		String provider = GPS;
		float accuracy = 4;
		long elapsedNanos = 0;
		int switches = 0;

		// Both report once a second, half a second apart, a little better or worse each time.
		for (int fix = 1; fix < 120; fix++) {
			String newProvider = fix % 2 == 0 ? GPS : FUSED;
			float newAccuracy = fix % 4 < 2 ? 3.5f : 4.5f;
			long newElapsedNanos = fix * SECOND_NANOS / 2;

			if (LocationFixChooser.shouldReplace(provider, accuracy, elapsedNanos, newProvider,
					newAccuracy, newElapsedNanos)) {
				if (!newProvider.equals(provider)) {
					switches++;
				}
				provider = newProvider;
				accuracy = newAccuracy;
				elapsedNanos = newElapsedNanos;
			}
		}

		assertEquals(0, switches);
	}

	@Test
	public void clearlyMoreAccurateProviderTakesOver() {
		assertTrue(LocationFixChooser.shouldReplace(NETWORK, 30, 0, GPS, 5, SECOND_NANOS));
	}

	@Test
	public void lessAccurateProviderDoesNotTakeOver() {
		assertFalse(LocationFixChooser.shouldReplace(GPS, 5, 0, NETWORK, 30, SECOND_NANOS));
	}

	@Test
	public void providerThatStoppedReportingIsReplaced() {
		assertTrue(LocationFixChooser.shouldReplace(GPS, 5, 0, NETWORK, 20, 30 * SECOND_NANOS));
	}

	@Test
	public void systemLastFixThatIsTheFixInUseIsTakenAgain() {
		assertTrue(LocationFixChooser.shouldReplace(GPS, 5, 100 * SECOND_NANOS, GPS, 5,
				100 * SECOND_NANOS));
	}

	@Test
	public void newerButCoarserSystemLastFixDoesNotReplaceRecentGpsFix() {
		// Back from a pause: another app's network fix came after the gps fix in use.
		assertFalse(LocationFixChooser.shouldReplace(GPS, 5, 100 * SECOND_NANOS, NETWORK, 30,
				110 * SECOND_NANOS));
	}

	@Test
	public void olderSystemLastFixFromAnotherProviderDoesNotReplaceTheFixInUse() {
		assertFalse(LocationFixChooser.shouldReplace(GPS, 5, 100 * SECOND_NANOS, FUSED, 4,
				90 * SECOND_NANOS));
	}

	private static final long NOW_NANOS = 3600 * SECOND_NANOS;
	private static final long MAX_AGE_NANOS = LocationFixChooser.LAST_KNOWN_FIX_MAX_AGE_NANOS;

	@Test
	public void lastFixJustInsideTheAgeLimitIsRecentEnough() {
		assertTrue(LocationFixChooser.isRecentEnough(NOW_NANOS - MAX_AGE_NANOS + 1, NOW_NANOS,
				MAX_AGE_NANOS));
	}

	@Test
	public void lastFixExactlyAtTheAgeLimitIsRecentEnough() {
		assertTrue(LocationFixChooser.isRecentEnough(NOW_NANOS - MAX_AGE_NANOS, NOW_NANOS,
				MAX_AGE_NANOS));
	}

	@Test
	public void lastFixJustOutsideTheAgeLimitIsTooOld() {
		assertFalse(LocationFixChooser.isRecentEnough(NOW_NANOS - MAX_AGE_NANOS - 1, NOW_NANOS,
				MAX_AGE_NANOS));
	}

	@Test
	public void lastFixFromHoursAgoIsTooOld() {
		// The phone was in a bag on the drive to the crag.
		assertFalse(LocationFixChooser.isRecentEnough(SECOND_NANOS, 3 * NOW_NANOS,
				MAX_AGE_NANOS));
	}

	@Test
	public void lastFixWithNoTimeIsTooOld() {
		assertFalse(LocationFixChooser.isRecentEnough(0, NOW_NANOS, MAX_AGE_NANOS));
		// Even right after boot, when the age would look small.
		assertFalse(LocationFixChooser.isRecentEnough(0, SECOND_NANOS, MAX_AGE_NANOS));
	}

	@Test
	public void lastFixSlightlyAheadOfNowIsRecentEnough() {
		assertTrue(LocationFixChooser.isRecentEnough(NOW_NANOS + SECOND_NANOS / 2, NOW_NANOS,
				MAX_AGE_NANOS));
	}
}
