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
	public void unwrappedBoundsWithinTheWorldStayAsTheyAre() {
		assertEquals(new MapBounds(46, 25, 45, 24), MapBounds.fromUnwrapped(46, 25, 45, 24));
		assertEquals(new MapBounds(10, 180, -10, 170), MapBounds.fromUnwrapped(10, 180, -10, 170));
		assertEquals(new MapBounds(10, -170, -10, -180),
				MapBounds.fromUnwrapped(10, -170, -10, -180));
	}

	@Test
	public void unwrappedBoundsPastTheAntimeridianCrossIt() {
		MapBounds eastward = MapBounds.fromUnwrapped(10, 190, -10, 170);
		MapBounds westward = MapBounds.fromUnwrapped(10, -170, -10, -190);

		assertEquals(new MapBounds(10, -170, -10, 170), eastward);
		assertEquals(new MapBounds(10, -170, -10, 170), westward);
		assertTrue(eastward.crossesAntimeridian());
		assertTrue(eastward.contains(new MapCoordinate(0, 175)));
		assertTrue(eastward.contains(new MapCoordinate(0, -175)));
		assertFalse(eastward.contains(new MapCoordinate(0, 0)));
	}

	@Test
	public void unwrappedBoundsOnAnotherCopyOfTheWorldMoveBack() {
		assertEquals(new MapBounds(10, -140, -10, -160), MapBounds.fromUnwrapped(10, 220, -10, 200));
		assertEquals(new MapBounds(10, 160, -10, 140), MapBounds.fromUnwrapped(10, -560, -10, -580));
		assertEquals(new MapBounds(10, -180, -10, -180), MapBounds.fromUnwrapped(10, 180, -10, 180));
	}

	@Test
	public void unwrappedBoundsWiderThanTheWorldCoverIt() {
		MapBounds bounds = MapBounds.fromUnwrapped(85, 250, -85, -250);

		assertEquals(new MapBounds(85, 180, -85, -180), bounds);
		assertEquals(bounds, MapBounds.fromUnwrapped(85, 180, -85, -180));
		assertEquals(bounds, MapBounds.fromUnwrapped(85, Double.POSITIVE_INFINITY, -85,
				Double.NEGATIVE_INFINITY));
		assertFalse(bounds.crossesAntimeridian());
		assertTrue(bounds.contains(new MapCoordinate(0, 0)));
		assertTrue(bounds.contains(new MapCoordinate(0, 179.9)));
		assertTrue(bounds.contains(new MapCoordinate(0, -179.9)));
	}

	@Test
	public void unwrappedBoundsStopAtThePoles() {
		assertEquals(new MapBounds(90, 25, -90, 24), MapBounds.fromUnwrapped(90.5, 25, -91, 24));
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
