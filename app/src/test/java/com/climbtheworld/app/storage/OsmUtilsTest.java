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
	public void countryQueryFetchesRelationsOfNodesAndWays() {
		String query = OsmUtils.buildCountryQuery("RO");

		assertTrue(query.contains("(bn.climbing);"));
		assertTrue(query.contains("(bw.climbing);"));
		assertTrue(query.contains(")->.parents;.parents out body center;"));
		assertTrue(query.contains("(br.parents);out body center;"));
	}
}
