package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsmUtilsTest {
	private static final String SPORT = "[\"sport\"~\"(^|;) *climbing *(;|$)\"]";
	private static final String LEISURE = "[\"leisure\"~\"^(sports_centre|sports_hall|pitch)$\"]";
	private static final String TOWER = "[\"tower:type\"=\"climbing\"]";
	private static final String BUILDING = "[\"building\"][\"building\"!=\"no\"]";
	private static final String GRADED_BOULDER = "[\"climbing\"=\"boulder\"][~\"^climbing:grade:\"~\".\"]";

	private final String query = OsmUtils.buildCountryQuery("RO");

	@Test
	public void routesAndCragNodesNeedNoSportButAName() {
		assertTrue(query.contains("node[\"climbing\"~\"^(route_bottom|route_top|route|crag)$\"]"
				+ "[\"name\"](area.searchArea);"));
		assertTrue(query.contains("node" + GRADED_BOULDER + "[\"name\"](area.searchArea);"));
	}

	@Test
	public void routeAndBoulderWaysComeWithTheirNodes() {
		assertTrue(query.contains("way[\"climbing\"=\"route\"](area.searchArea);"));
		assertTrue(query.contains("way" + GRADED_BOULDER + "(area.searchArea);"));
		assertTrue(query.contains(".collections;.collections >;)->.climbing;"));
	}

	@Test
	public void facilitiesNeedTheClimbingSport() {
		for (String elements : new String[]{"node", "way", "rel"}) {
			String suffix = elements.equals("node") ? "[\"name\"](area.searchArea);"
					: "(area.searchArea);";
			assertTrue(elements, query.contains(elements + SPORT + LEISURE + suffix));
			assertTrue(elements, query.contains(elements + SPORT + TOWER + suffix));
		}
		// Only areas are climbing halls.
		assertTrue(query.contains("way" + SPORT + BUILDING + "(area.searchArea);"));
		assertTrue(query.contains("rel" + SPORT + BUILDING + "(area.searchArea);"));
		assertFalse(query.contains("node" + SPORT + BUILDING));
	}

	@Test
	public void sportPatternFindsClimbingAmongOtherSports() {
		Pattern sport = Pattern.compile("(^|;) *climbing *(;|$)");

		assertTrue(sport.matcher("climbing").find());
		assertTrue(sport.matcher("hiking;climbing").find());
		assertTrue(sport.matcher("hiking; climbing").find());
		assertTrue(sport.matcher("climbing ;hiking").find());
		assertFalse(sport.matcher("ice_climbing").find());
		assertFalse(sport.matcher("climbing_adventure").find());
		assertFalse(sport.matcher("hiking").find());
	}

	@Test
	public void nothingIsFetchedForSportClimbingAlone() {
		// Every statement filters on more than sport=climbing, so cliffs, peaks and the like stay
		// on the server.
		Matcher statement = Pattern.compile("(node|way|rel)((?:\\[[^\\]]*\\])+)\\(area\\.searchArea\\);")
				.matcher(query);
		int statements = 0;
		while (statement.find()) {
			statements++;
			assertFalse(statement.group(), statement.group(2).replace("[\"name\"]", "").equals(SPORT));
		}
		assertEquals(12, statements);
	}

	@Test
	public void sitesAndRouteRelationsAreFoundFromTheirMembersAtAnyDepth() {
		assertTrue(query.contains(".climbing out body center;"
				+ "(rel(bn.climbing);rel(bw.climbing);)->.parents;(.parents;.parents <<;)->.ancestors;"));
		assertTrue(query.contains("rel.ancestors[\"type\"=\"site\"][\"climbing\"~\"^(crag|area)$\"];"));
		assertTrue(query.contains("rel.ancestors[\"climbing\"=\"route\"];"));
		assertTrue(query.endsWith(");out body center;"));
	}
}
