package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * SuggestionsOutOfSight.
 */
public class SuggestionsOutOfSightTest {
	// Four routes along a strip of 100, not in the strip's order, as the suggestions come best
	// first: their middles at 85, 5, 45 and 25.
	private static final float[] SPANS = {80, 90, 0, 10, 40, 50, 20, 30};

	@Test
	public void nothingIsOutOfSightWhenTheWholeStripShows() {
		SuggestionsOutOfSight outOfSight = SuggestionsOutOfSight.of(SPANS, 0, 100);
		assertEquals(0, outOfSight.before);
		assertEquals(0, outOfSight.after);
		assertEquals(-1, outOfSight.nearestBefore);
		assertEquals(-1, outOfSight.nearestAfter);
	}

	@Test
	public void routesAreCountedOnTheSideTheyAreOutOfSight() {
		SuggestionsOutOfSight outOfSight = SuggestionsOutOfSight.of(SPANS, 30, 60);
		assertEquals(2, outOfSight.before);
		assertEquals(1, outOfSight.after);
	}

	@Test
	public void theNearestOnEachSideIsTheOneNextToThePartInSight() {
		SuggestionsOutOfSight outOfSight = SuggestionsOutOfSight.of(SPANS, 30, 40);
		// At 25, rather than at 5.
		assertEquals(3, outOfSight.nearestBefore);
		// At 45, rather than at 85.
		assertEquals(2, outOfSight.nearestAfter);
	}

	@Test
	public void aRouteIsInSightWhileItsMiddleIs() {
		// The route from 20 to 30, half of it showing: only the one from 0 to 10 is out.
		assertEquals(1, SuggestionsOutOfSight.of(SPANS, 25, 60).before);
		// Less than half of it.
		assertEquals(2, SuggestionsOutOfSight.of(SPANS, 26, 60).before);
		// The route from 40 to 50, at the other end.
		assertEquals(1, SuggestionsOutOfSight.of(SPANS, 0, 45).after);
		assertEquals(2, SuggestionsOutOfSight.of(SPANS, 0, 44).after);
	}

	@Test
	public void noSuggestionsMeansNoneOutOfSight() {
		SuggestionsOutOfSight outOfSight = SuggestionsOutOfSight.of(new float[0], 10, 20);
		assertEquals(0, outOfSight.before);
		assertEquals(0, outOfSight.after);
		assertEquals(-1, outOfSight.nearestBefore);
		assertEquals(-1, outOfSight.nearestAfter);
	}
}
