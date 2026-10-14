package com.climbtheworld.app.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCameraState;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.style.MapStyleRegistry;

import org.junit.Test;

import java.util.Arrays;

public class MapContractTest {
	@Test
	public void boundsContainCoordinatesWithoutCrossingAntimeridian() {
		MapBounds bounds = new MapBounds(46, 25, 45, 24);

		assertTrue(bounds.contains(new MapCoordinate(45.5, 24.5)));
		assertFalse(bounds.contains(new MapCoordinate(45.5, 23.5)));
		assertFalse(bounds.crossesAntimeridian());
	}

	@Test
	public void boundsContainCoordinatesAcrossAntimeridian() {
		MapBounds bounds = new MapBounds(10, -170, -10, 170);

		assertTrue(bounds.crossesAntimeridian());
		assertTrue(bounds.contains(new MapCoordinate(0, 175)));
		assertTrue(bounds.contains(new MapCoordinate(0, -175)));
		assertFalse(bounds.contains(new MapCoordinate(0, 0)));
	}

	@Test
	public void cameraStatePreservesNeutralCoordinate() {
		MapCoordinate coordinate = new MapCoordinate(45.35384, 24.63507, 100);
		MapCameraState camera = new MapCameraState(coordinate, 16);

		assertEquals(coordinate, camera.getCenter());
		assertEquals(16, camera.getZoom(), 0);
	}

	@Test
	public void styleRegistryUsesOpenFreeMapLibertyByDefault() {
		assertEquals("openfreemap-liberty", MapStyleRegistry.getDefaultStyle().getId());
		assertEquals("https://tiles.openfreemap.org/styles/liberty",
				MapStyleRegistry.getDefaultStyle().getStyleUrl());
		assertEquals(MapStyleRegistry.getDefaultStyle(), MapStyleRegistry.getStyle("missing"));
	}

	@Test
	public void styleRegistryOffersOpenTopoMapAfterTheDefault() {
		assertEquals(MapStyleRegistry.OPEN_TOPO_MAP, MapStyleRegistry.getStyle("opentopomap"));
		assertEquals("asset://maplibre/opentopomap.json",
				MapStyleRegistry.OPEN_TOPO_MAP.getStyleUrl());
		assertEquals(Arrays.asList(MapStyleRegistry.OPEN_FREE_MAP_LIBERTY,
				MapStyleRegistry.OPEN_TOPO_MAP), MapStyleRegistry.getAvailableStyles());
	}

	@Test
	public void coordinateRoundTripsThroughDelimitedString() {
		MapCoordinate coordinate = new MapCoordinate(45.35384, 24.63507, 100);

		assertEquals(coordinate, MapCoordinate.fromDelimitedString(coordinate.toDelimitedString()));
	}
}
