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
import java.util.Collections;

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
	public void longitudeSpanCountsAcrossTheAntimeridian() {
		assertEquals(1, new MapBounds(46, 25, 45, 24).getLongitudeSpan(), 0);
		assertEquals(20, new MapBounds(10, -170, -10, 170).getLongitudeSpan(), 0);
		assertEquals(360, new MapBounds(10, 180, -10, -180).getLongitudeSpan(), 0);
	}

	@Test
	public void paddedBoundsGrowOnEverySide() {
		assertBounds(new MapBounds(49, 29, 43, 23), new MapBounds(48, 28, 44, 24).padded(0.25));
	}

	@Test
	public void paddedBoundsAcrossTheAntimeridianGrowOnBothSidesOfIt() {
		assertBounds(new MapBounds(20, -170, -20, 170),
				new MapBounds(10, -175, -10, 175).padded(0.5));
	}

	@Test
	public void paddedBoundsWrapPastTheAntimeridian() {
		MapBounds padded = new MapBounds(10, 179, -10, 171).padded(0.25);

		assertBounds(new MapBounds(15, -179, -15, 169), padded);
		assertTrue(padded.crossesAntimeridian());
	}

	@Test
	public void paddedBoundsStopAtTheWorld() {
		assertEquals(new MapBounds(90, 180, -90, -180),
				new MapBounds(80, 170, -80, -170).padded(0.25));
	}

	@Test
	public void boundsSplitAtTheAntimeridianOnlyWhenCrossingIt() {
		MapBounds bounds = new MapBounds(46, 25, 45, 24);
		MapBounds world = new MapBounds(10, 180, -10, -180);

		assertEquals(Collections.singletonList(bounds), bounds.splitAtAntimeridian());
		assertEquals(Collections.singletonList(world), world.splitAtAntimeridian());
		assertEquals(Arrays.asList(new MapBounds(10, 180, -10, 170),
						new MapBounds(10, -170, -10, -180)),
				new MapBounds(10, -170, -10, 170).splitAtAntimeridian());
	}

	@Test
	public void boundsAroundPointsHoldThemAll() {
		assertEquals(new MapBounds(46, 25, 45, 24), MapBounds.around(Arrays.asList(
				new MapCoordinate(45, 25), new MapCoordinate(46, 24), new MapCoordinate(45.5, 24.5))));
		assertEquals(new MapBounds(45, 24, 45, 24),
				MapBounds.around(Collections.singletonList(new MapCoordinate(45, 24))));
	}

	@Test
	public void boundsAroundPointsAcrossGreenwichDoNotCrossTheAntimeridian() {
		MapBounds bounds = MapBounds.around(Arrays.asList(
				new MapCoordinate(43, -0.01), new MapCoordinate(43.01, 0.01)));

		assertEquals(new MapBounds(43.01, 0.01, 43, -0.01), bounds);
		assertFalse(bounds.crossesAntimeridian());
	}

	@Test
	public void boundsAroundPointsAcrossTheAntimeridianCrossIt() {
		// The ones nearest the antimeridian are not the sides of the bounds.
		MapBounds bounds = MapBounds.around(Arrays.asList(new MapCoordinate(-16.8, 179.5),
				new MapCoordinate(-16.8, 179.9), new MapCoordinate(-16.9, -179.8),
				new MapCoordinate(-16.9, -179.6)));

		assertEquals(new MapBounds(-16.8, -179.6, -16.9, 179.5), bounds);
		assertTrue(bounds.crossesAntimeridian());
	}

	@Test
	public void boundsAroundPointsTakeTheNarrowerWayRound() {
		// 170 degrees across Greenwich, 190 across the antimeridian.
		assertEquals(new MapBounds(10, 90, 0, -80), MapBounds.around(Arrays.asList(
				new MapCoordinate(0, -80), new MapCoordinate(10, 90))));
		// 190 degrees across Greenwich, 170 across the antimeridian.
		assertEquals(new MapBounds(10, -100, 0, 90), MapBounds.around(Arrays.asList(
				new MapCoordinate(0, 90), new MapCoordinate(10, -100))));
	}

	@Test
	public void centerIsCountedAcrossTheAntimeridian() {
		assertEquals(new MapCoordinate(45.5, 24.5), new MapBounds(46, 25, 45, 24).getCenter());
		assertEquals(new MapCoordinate(0, 180), new MapBounds(10, -170, -10, 170).getCenter());
		assertEquals(new MapCoordinate(0, -175), new MapBounds(10, -170, -10, 180).getCenter());
		assertEquals(new MapCoordinate(0, 175), new MapBounds(10, -180, -10, 170).getCenter());
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

	private static void assertBounds(MapBounds expected, MapBounds actual) {
		assertEquals(expected.getNorth(), actual.getNorth(), 1e-9);
		assertEquals(expected.getEast(), actual.getEast(), 1e-9);
		assertEquals(expected.getSouth(), actual.getSouth(), 1e-9);
		assertEquals(expected.getWest(), actual.getWest(), 1e-9);
	}
}
