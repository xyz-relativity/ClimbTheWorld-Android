package com.climbtheworld.app.navigate.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CompassWidgetTest {
	@Test
	public void needleGlidesTheSameAtAnyFrameRate() {
		// 48 ms is three frames at 60 Hz and six at 120 Hz.
		double at60Hz = remainingAfter(90, 3, 16);
		double at120Hz = remainingAfter(90, 6, 8);

		assertEquals(at60Hz, at120Hz, 1e-9);
		assertEquals(90 * Math.exp(-0.048 / CompassWidget.FOLLOW_SECONDS), at60Hz, 1e-9);
	}

	@Test
	public void needleReachesTheReadingQuickly() {
		assertTrue(remainingAfter(90, 18, 16) < 0.5);
	}

	@Test
	public void needleStaysPutWhenNoTimePassed() {
		assertEquals(0, CompassWidget.followStep(90, 0), 0);
	}

	private static double remainingAfter(double degrees, int frames, long frameMs) {
		double remaining = degrees;
		for (int frame = 0; frame < frames; frame++) {
			remaining -= CompassWidget.followStep(remaining, frameMs);
		}
		return remaining;
	}
}
