package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AlignmentOrder.
 */
public class AlignmentOrderTest {
	private static List<Long> ordered(List<Long> closestFirst, List<List<Long>> crags) {
		return AlignmentOrder.ordered(closestFirst, crags, Long::longValue);
	}

	/**
	 * Each group as its crag, then its routes.
	 */
	private static List<List<Long>> described(List<AlignmentOrder.Group<Long>> groups) {
		return groups.stream().map(group -> {
			List<Long> described = new ArrayList<>();
			described.add((long) group.crag);
			described.addAll(group.routes);
			return described;
		}).collect(Collectors.toList());
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

	@Test
	public void groupsTheRoutesByCragInTheOrderTheyCome() {
		List<List<Long>> crags = List.of(List.of(1L, 2L), List.of(11L, 12L));
		assertEquals(List.of(List.of(1L, 11L, 12L), List.of(0L, 1L, 2L),
						List.of((long) AlignmentOrder.NO_CRAG, 9L)),
				described(AlignmentOrder.grouped(List.of(12L, 9L, 2L, 1L, 11L), crags,
						Long::longValue)));
	}

	@Test
	public void noGroupOfNoCragWhenAllAreInOne() {
		assertEquals(List.of(List.of(0L, 1L, 2L)),
				described(AlignmentOrder.grouped(List.of(2L, 1L), List.of(List.of(1L, 2L)),
						Long::longValue)));
	}

	@Test
	public void regroupsWhatIsLeftOfEachCrag() {
		Map<Long, Integer> crags = Map.of(1L, 0, 2L, 0, 11L, 1, 9L, AlignmentOrder.NO_CRAG);
		assertEquals(List.of(List.of(0L, 2L), List.of(1L, 11L),
						List.of((long) AlignmentOrder.NO_CRAG, 9L)),
				described(AlignmentOrder.regrouped(List.of(2L, 11L, 9L), crags::get)));
	}

	@Test
	public void regroupsNothingIntoNoGroup() {
		assertEquals(List.of(), AlignmentOrder.regrouped(List.<Long>of(), route -> 0));
	}
}
