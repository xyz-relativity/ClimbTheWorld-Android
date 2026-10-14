package com.climbtheworld.app.map.style;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MapStyleRegistryTest {
	@Test
	public void openTopoMapIsABundledStyle() {
		assertEquals(MapStyleRegistry.OPEN_TOPO_MAP, MapStyleRegistry.getStyle("opentopomap"));
		assertEquals("asset://maplibre/opentopomap.json",
				MapStyleRegistry.OPEN_TOPO_MAP.getStyleUrl());
		assertFalse(MapStyleRegistry.OPEN_TOPO_MAP.isMapboxLogoRequired());
	}

	@Test
	public void buildsWithoutMapboxTokenLeaveSatelliteOut() {
		assertEquals(Arrays.asList(MapStyleRegistry.OPEN_FREE_MAP_LIBERTY,
				MapStyleRegistry.OPEN_TOPO_MAP), MapStyleRegistry.availableStyles(""));
	}

	@Test
	public void buildsWithMapboxTokenOfferSatelliteLast() {
		List<MapStyleDefinition> styles = MapStyleRegistry.availableStyles("pk.test-token_1");

		assertEquals(3, styles.size());
		assertEquals(Arrays.asList(MapStyleRegistry.OPEN_FREE_MAP_LIBERTY,
				MapStyleRegistry.OPEN_TOPO_MAP), styles.subList(0, 2));
		MapStyleDefinition satellite = styles.get(2);
		assertEquals(MapStyleRegistry.MAPBOX_SATELLITE_ID, satellite.getId());
		assertTrue(satellite.isMapboxLogoRequired());
		assertNull(satellite.getStyleUrl());
		assertTrue(satellite.getStyleJson().contains(
				"https://api.mapbox.com/v4/mapbox.satellite/{z}/{x}/{y}@2x.jpg90"
						+ "?access_token=pk.test-token_1\""));
		assertFalse(satellite.getStyleJson().contains("ACCESS_TOKEN"));
	}
}
