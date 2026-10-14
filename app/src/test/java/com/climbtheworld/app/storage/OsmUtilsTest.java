package com.climbtheworld.app.storage;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OsmUtilsTest {
	@Test
	public void countryQueryRequiresNodeNames() {
		String query = OsmUtils.buildCountryQuery("RO");

		assertTrue(query.contains("[\"name\"](area.searchArea)"));
	}

	@Test
	public void countryQueryFetchesEveryArtificialClimbingNode() {
		String query = OsmUtils.buildCountryQuery("RO");

		for (String facility : new String[]{"[\"leisure\"=\"sports_centre\"]",
				"[\"leisure\"=\"sports_hall\"]", "[\"leisure\"=\"pitch\"]",
				"[\"tower:type\"=\"climbing\"]", "[\"building\"]"}) {
			assertTrue(facility, query.contains(facility + "[\"name\"](area.searchArea);"));
		}
	}

	@Test
	public void countryQueryFetchesRelationsOfNodesAndWays() {
		String query = OsmUtils.buildCountryQuery("RO");

		assertTrue(query.contains("(bn.climbing);"));
		assertTrue(query.contains("(bw.climbing);"));
		assertTrue(query.contains(")->.parents;.parents out body center;"));
		assertTrue(query.contains("(br.parents);out body center;"));
	}
}
