package com.climbtheworld.app.storage;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OsmUtilsTest {
	@Test
	public void countryQueryRequiresNodeNames() {
		String query = OsmUtils.buildCountryQuery("RO");

		assertTrue(query.contains("[\"name\"](area.searchArea)"));
	}
}
