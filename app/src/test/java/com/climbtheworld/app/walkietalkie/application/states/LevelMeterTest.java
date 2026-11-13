package com.climbtheworld.app.walkietalkie.application.states;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LevelMeterTest {
	private static final long FRAME_MS = 20;

	private final LevelMeter meter = new LevelMeter();

	@Test
	public void theFirstLevelIsShown() {
		assertEquals(50, meter.next(0.5, 0, true));
	}

	@Test
	public void silenceLeavesTheBarAlone() {
		assertEquals(0, meter.next(0, 0, true));

		for (int frame = 1; frame < 500; frame++) {
			assertEquals(LevelMeter.NONE, meter.next(0.001, frame * FRAME_MS, true));
		}
	}

	@Test
	public void theSameLevelIsNotShownAgain() {
		assertEquals(50, meter.next(0.5, 0, true));

		assertEquals(LevelMeter.NONE, meter.next(0.5, 1000, true));
	}

	@Test
	public void theBarChangesAFewTimesASecondAtMost() {
		assertEquals(10, meter.next(0.1, 0, true));

		// The next frames are louder each, but too soon after the bar changed.
		assertEquals(LevelMeter.NONE, meter.next(0.2, FRAME_MS, true));
		assertEquals(LevelMeter.NONE, meter.next(0.3, 2 * FRAME_MS, true));
		assertEquals(40, meter.next(0.4, LevelMeter.MIN_INTERVAL_MS, true));
	}

	@Test
	public void aLevelHeldBackIsShownOnceItMayEvenIfNothingChangesAnyMore() {
		assertEquals(10, meter.next(0.1, 0, true));
		assertEquals(LevelMeter.NONE, meter.next(0.9, FRAME_MS, true));

		// The same peak again: the bar still shows 10.
		assertEquals(90, meter.next(0.9, LevelMeter.MIN_INTERVAL_MS, true));
	}

	@Test
	public void thePeakFallsOffOverTheNextFramesDownToNothing() {
		assertEquals(100, meter.next(1, 0, true));

		assertEquals(57, meter.next(0, 1000, true));
		assertEquals(33, meter.next(0, 2000, true));
		int level = 33;
		for (int frame = 3; level != 0 && frame < 50; frame++) {
			level = meter.next(0, frame * 1000L, true);
		}
		assertEquals(0, level);
		assertEquals(LevelMeter.NONE, meter.next(0, 100_000, true));
	}

	@Test
	public void nothingIsShownWhileTheBarIsOffScreen() {
		assertEquals(LevelMeter.NONE, meter.next(0.5, 0, false));
		assertEquals(LevelMeter.NONE, meter.next(0.8, 1000, false));
	}

	@Test
	public void theLevelShowsAgainOnceTheBarIsBackOnScreen() {
		assertEquals(50, meter.next(0.5, 0, true));
		assertEquals(LevelMeter.NONE, meter.next(0.8, 1000, false));

		assertEquals(80, meter.next(0.8, 2000, true));
	}

	@Test
	public void theLevelIsShownAgainAfterTheBarWasSetBySomethingElse() {
		assertEquals(50, meter.next(0.5, 0, true));

		// Set to 0, as when a recording is done, with the microphone still as loud.
		meter.barChanged();

		assertEquals(50, meter.next(0.5, FRAME_MS, true));
	}
}
