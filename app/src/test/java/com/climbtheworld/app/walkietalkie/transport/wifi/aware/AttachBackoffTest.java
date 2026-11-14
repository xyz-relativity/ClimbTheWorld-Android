package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttachBackoffTest {
	@Test
	public void delayDoublesUpToTheCap() {
		AttachBackoff backoff = new AttachBackoff();
		long[] expected = {2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000, 60_000};
		for (long delayMs : expected) {
			assertEquals(delayMs, backoff.nextDelayMs());
		}
	}

	@Test
	public void attachResetsTheDelay() {
		AttachBackoff backoff = new AttachBackoff();
		for (int i = 0; i < 10; i++) {
			backoff.nextDelayMs();
		}
		backoff.onAttached();
		assertEquals(2_000, backoff.nextDelayMs());
		assertEquals(4_000, backoff.nextDelayMs());
	}

	@Test
	public void availabilityResetsTheDelay() {
		AttachBackoff backoff = new AttachBackoff();
		backoff.nextDelayMs();
		backoff.nextDelayMs();
		backoff.onAvailable();
		assertEquals(2_000, backoff.nextDelayMs());
	}

	@Test
	public void failureShownOncePerRunOfFailures() {
		AttachBackoff backoff = new AttachBackoff();
		assertTrue(backoff.showFailure());
		assertFalse(backoff.showFailure());
		assertFalse(backoff.showFailure());
		backoff.onAttached();
		assertTrue(backoff.showFailure());
		assertFalse(backoff.showFailure());
	}

	@Test
	public void availabilityDoesNotShowTheFailureAgain() {
		AttachBackoff backoff = new AttachBackoff();
		assertTrue(backoff.showFailure());
		backoff.onAvailable();
		assertFalse(backoff.showFailure());
	}
}
