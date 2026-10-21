package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * AlignmentRange.
 */
public class AlignmentRangeTest {
	@Test
	public void startsFar() {
		assertFalse(new AlignmentRange().isNear());
	}

	@Test
	public void comesNearWithinTheNearDistance() {
		AlignmentRange range = new AlignmentRange();
		assertFalse(range.update(AlignmentRange.NEAR_METERS + 1));
		assertTrue(range.update(AlignmentRange.NEAR_METERS));
	}

	@Test
	public void staysNearUntilPastTheFarDistance() {
		AlignmentRange range = new AlignmentRange();
		range.update(10);
		assertTrue(range.update(AlignmentRange.NEAR_METERS + 1));
		assertTrue(range.update(AlignmentRange.FAR_METERS - 1));
		assertFalse(range.update(AlignmentRange.FAR_METERS));
	}

	@Test
	public void staysFarUntilWithinTheNearDistance() {
		AlignmentRange range = new AlignmentRange();
		range.update(100);
		assertFalse(range.update(AlignmentRange.FAR_METERS - 1));
		assertFalse(range.update(AlignmentRange.NEAR_METERS + 1));
		assertTrue(range.update(AlignmentRange.NEAR_METERS - 1));
	}

	@Test
	public void countsNoKnownRouteAsFar() {
		AlignmentRange range = new AlignmentRange();
		range.update(10);
		assertFalse(range.update(Double.NaN));
	}
}
