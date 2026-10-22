package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/**
 * AlignmentOrder.
 */
public class AlignmentOrderTest {
	private static List<Long> ordered(List<Long> closestFirst, List<List<Long>> crags) {
		return AlignmentOrder.ordered(closestFirst, crags, Long::longValue);
	}

	@Test
	public void listsACragsRoutesInItsOrder() {
		assertEquals(List.of(1L, 2L, 3L, 4L),
				ordered(List.of(3L, 2L, 4L, 1L), List.of(List.of(1L, 2L, 3L, 4L))));
	}

	@Test
	public void theCragOfTheClosestRouteComesFirst() {
		List<List<Long>> crags = List.of(List.of(1L, 2L), List.of(11L, 12L));
		assertEquals(List.of(11L, 12L, 1L, 2L),
				ordered(List.of(12L, 2L, 1L, 11L), crags));
	}

	@Test
	public void routesOfNoCragComeLastClosestFirst() {
		assertEquals(List.of(1L, 2L, 9L, 8L),
				ordered(List.of(9L, 2L, 8L, 1L), List.of(List.of(1L, 2L))));
	}

	@Test
	public void withoutCragsTheClosestComeFirst() {
		assertEquals(List.of(3L, 1L, 2L), ordered(List.of(3L, 1L, 2L), List.of()));
	}

	@Test
	public void aRouteInTwoCragsIsListedOnceWithTheFirst() {
		List<List<Long>> crags = List.of(List.of(1L, 2L), List.of(2L, 3L));
		assertEquals(List.of(1L, 2L, 3L), ordered(List.of(1L, 3L, 2L), crags));
	}

	@Test
	public void membersNotAroundAreSkipped() {
		assertEquals(List.of(1L, 3L),
				ordered(List.of(3L, 1L), List.of(List.of(1L, 2L, 3L, 4L))));
	}
}
