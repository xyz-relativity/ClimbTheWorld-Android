package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AlignmentOrder.
 */
public class AlignmentOrderTest {
	// The OSM ID of the first crag, see sites; the areas' are 1000 and up.
	private static final long CRAG_ID = 100;

	private static List<Long> ordered(List<Long> closestFirst, List<List<Long>> crags) {
		return ordered(closestFirst, crags, List.of());
	}

	/**
	 * @param crags each crag's routes, see sites
	 * @param areas each area's crags and areas
	 */
	private static List<Long> ordered(List<Long> closestFirst, List<List<Long>> crags,
	                                  List<AlignmentOrder.Site> areas) {
		return AlignmentOrder.ordered(closestFirst, sites(crags), areas, Long::longValue);
	}

	private static List<AlignmentOrder.Group<Long>> grouped(List<Long> closestFirst,
	                                                        List<List<Long>> crags,
	                                                        List<AlignmentOrder.Site> areas) {
		return AlignmentOrder.grouped(closestFirst, sites(crags), areas, Long::longValue);
	}

	/**
	 * The crags, each with the routes given, its OSM ID CRAG_ID plus its index.
	 */
	private static List<AlignmentOrder.Site> sites(List<List<Long>> crags) {
		List<AlignmentOrder.Site> sites = new ArrayList<>();
		for (int crag = 0; crag < crags.size(); crag++) {
			sites.add(new AlignmentOrder.Site(CRAG_ID + crag, crags.get(crag)));
		}
		return sites;
	}

	private static AlignmentOrder.Site area(long id, Long... members) {
		return new AlignmentOrder.Site(id, List.of(members));
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
				described(grouped(List.of(12L, 9L, 2L, 1L, 11L), crags, List.of())));
	}

	@Test
	public void noGroupOfNoCragWhenAllAreInOne() {
		assertEquals(List.of(List.of(0L, 1L, 2L)),
				described(grouped(List.of(2L, 1L), List.of(List.of(1L, 2L)), List.of())));
	}

	@Test
	public void cragsComeInTheirAreasOrder() {
		List<List<Long>> crags = List.of(List.of(1L, 2L), List.of(11L, 12L), List.of(21L, 22L));
		assertEquals(List.of(1L, 2L, 11L, 12L, 21L, 22L),
				ordered(List.of(11L, 21L, 1L, 22L, 12L, 2L), crags,
						List.of(area(1000, 100L, 101L, 102L))));
	}

	@Test
	public void anAreaInAnAreaListsItsCragsWhereTheAreaListsIt() {
		List<List<Long>> crags = List.of(List.of(1L), List.of(2L), List.of(3L));
		// The inner area first, as the database may give them.
		List<AlignmentOrder.Site> areas = List.of(area(1001, 101L), area(1000, 100L, 1001L, 102L));
		assertEquals(List.of(1L, 2L, 3L), ordered(List.of(2L, 3L, 1L), crags, areas));
	}

	@Test
	public void theAreasComeByTheirClosestRouteACragOfNoAreaAlone() {
		List<List<Long>> crags = List.of(List.of(1L), List.of(2L), List.of(3L), List.of(4L));
		List<AlignmentOrder.Site> areas = List.of(area(1000, 100L, 101L), area(1001, 103L));
		assertEquals(List.of(3L, 4L, 1L, 2L), ordered(List.of(3L, 4L, 2L, 1L), crags, areas));
	}

	@Test
	public void aCragOfTheAreaWithNoRouteAroundHasNoGroup() {
		List<List<Long>> crags = List.of(List.of(1L), List.of(5L), List.of(3L));
		assertEquals(List.of(List.of(2L, 3L), List.of(0L, 1L)),
				described(grouped(List.of(3L, 1L), crags,
						List.of(area(1000, 102L, 101L, 999L, 100L)))));
	}

	@Test
	public void aCragInTwoAreasIsListedOnce() {
		List<List<Long>> crags = List.of(List.of(1L), List.of(2L));
		List<AlignmentOrder.Site> areas = List.of(area(1000, 100L), area(1001, 101L, 100L));
		assertEquals(List.of(List.of(0L, 1L), List.of(1L, 2L)),
				described(grouped(List.of(1L, 2L), crags, areas)));
	}

	@Test
	public void areasListingEachOtherStillListEveryCrag() {
		List<List<Long>> crags = List.of(List.of(1L), List.of(2L));
		List<AlignmentOrder.Site> areas = List.of(area(1000, 1001L, 100L),
				area(1001, 1000L, 101L));
		List<Long> ordered = ordered(List.of(2L, 1L), crags, areas);
		assertEquals(2, ordered.size());
		assertEquals(Set.of(1L, 2L), Set.copyOf(ordered));
	}

	@Test
	public void routesOfNoCragStillComeLastWithAreas() {
		List<List<Long>> crags = List.of(List.of(1L), List.of(2L));
		assertEquals(List.of(1L, 2L, 9L),
				ordered(List.of(9L, 2L, 1L), crags, List.of(area(1000, 100L, 101L))));
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
