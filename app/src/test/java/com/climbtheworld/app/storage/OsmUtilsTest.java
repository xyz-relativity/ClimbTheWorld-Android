package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsmUtilsTest {
	private static final String SPORT = "[\"sport\"~\"(^|;) *climbing *(;|$)\"]";
	private static final String CLIMBING_NODE = "[\"climbing\"~\"^(route_bottom|route_top|route|crag|boulder)$\"]";
	private static final String CLIMBING_WAY = "[\"climbing\"~\"^(route|boulder)$\"]";
	private static final String LEISURE = "[\"leisure\"~\"^(sports_centre|sports_hall|pitch)$\"]";
	private static final String TOWER = "[\"tower:type\"=\"climbing\"]";
	private static final String BUILDING = "[\"building\"][\"building\"!=\"no\"]";
	private static final String NAMED = "[\"name\"]";

	private final String query = OsmUtils.buildCountryQuery("RO");

	@Test
	public void fourStatementsLookInTheArea() {
		// Each statement in the area reads the whole area, which is slow for a big country.
		assertEquals(4, query.split("\\(area\\.searchArea\\)", -1).length - 1);
		assertTrue(query.contains("node" + CLIMBING_NODE + "(area.searchArea)->.climbingNodes;"
				+ "node" + SPORT + "(area.searchArea)->.sportNodes;"
				+ "way" + CLIMBING_WAY + "(area.searchArea)->.routeWays;"
				+ "way" + SPORT + "(area.searchArea)->.sportWays;"));
	}

	@Test
	public void statementsInTheAreaHaveASingleValuePatternOnARareKey() {
		// Overpass resolves the filters of such a statement over the whole planet, exact values
		// first, then bare keys, then value patterns, and gives up on its index once a filter has
		// matched a million elements, after which it loads everything in the area. A bare key
		// such as name would be read for every element that has one.
		Matcher statement = Pattern.compile("\\b(node|way|rel)((?:\\[[^\\]]*\\])*)\\(area\\.searchArea\\)")
				.matcher(query);
		int statements = 0;
		while (statement.find()) {
			statements++;
			assertFalse(statement.group(), "rel".equals(statement.group(1)));
			assertTrue(statement.group(), statement.group(2).matches("\\[\"(climbing|sport)\"~\"[^\"]*\"\\]"));
		}
		assertEquals(4, statements);
	}

	@Test
	public void namesAndFacilityTagsAreCheckedOnTheSets() {
		assertTrue(query.contains("(node.climbingNodes" + NAMED + ";"
				+ "node.sportNodes" + LEISURE + NAMED + ";"
				+ "node.sportNodes" + TOWER + NAMED + ";"));
		assertTrue(query.contains("way.sportWays" + LEISURE + ";"
				+ "way.sportWays" + TOWER + ";"
				+ "way.sportWays" + BUILDING + ";)->.climbing;"));
		// Only areas are climbing halls.
		assertFalse(query.contains("node.sportNodes" + BUILDING));
	}

	@Test
	public void routeWaysComeWithTheirNodesAndFacilitiesWithout() {
		assertTrue(query.contains(";.routeWays;.routeWays >;"));
		assertFalse(query.contains(".sportWays >"));
		assertFalse(query.contains(".climbing >"));
	}

	@Test
	public void relationsAreFoundFromTheirMembersAtAnyDepth() {
		assertTrue(query.contains("(rel(bn.climbingNodes);rel(bw.routeWays);)->.parents;"
				+ "(.parents;.parents <<;)->.ancestors;"
				+ "(rel.ancestors[\"climbing\"];rel.ancestors" + SPORT + ";)->.relations;"));
	}

	@Test
	public void theFeaturesAreSentThenTheRelations() {
		assertTrue(query.startsWith("[out:json][timeout:"));
		assertTrue(query.contains("area[type=boundary][\"ISO3166-1\"=\"RO\"]->.searchArea;"));
		assertTrue(query.endsWith(")->.climbing;.climbing out body center;.relations out body center;"));
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
	public void noFilterIsOnAPatternOfKeys() {
		assertFalse(query.contains("[~"));
	}
}
