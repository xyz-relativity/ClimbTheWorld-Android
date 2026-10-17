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
	public void placeholderIsReplacedByTheFirstFix() {
		assertTrue(LocationFixChooser.shouldReplace("passive", 999, 0, NETWORK, 40,
				SECOND_NANOS));
	}
}
