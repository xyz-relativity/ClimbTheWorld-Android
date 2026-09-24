package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.model.MapCoordinate;

import org.junit.Test;

import java.util.Arrays;

public class ClimbingGeometryBuilderTest {
	private static final String EMPTY_FEATURE_COLLECTION =
			"{\"type\":\"FeatureCollection\",\"features\":[]}";
	private final ClimbingGeometryBuilder builder = new ClimbingGeometryBuilder();

	@Test
	public void areaLabelContainsEscapedNameAndCountBadgeBelowCragZoom() {
		ClimbingGeometryBuilder.GeometrySpec area = geometry(
				9, 11, "Grand \"Wall\"", 12);

		String labels = builder.buildHullLabelGeoJson(Arrays.asList(area), 10.5);

		assertTrue(labels.contains("\"name\":\"Grand \\\"Wall\\\"\""));
		assertTrue(labels.contains("\"badgeIcon\":\"ctw-hull-count-12\""));
		assertTrue(labels.contains("\"coordinates\":[24.5,45.5]"));
	}

	@Test
	public void areaLabelIsHiddenWhenCragsStartShowing() {
		ClimbingGeometryBuilder.GeometrySpec area = geometry(9, 11, "Grand Wall", 12);

		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullLabelGeoJson(Arrays.asList(area), 11));
	}

	@Test
	public void cragLabelIsHiddenWhenRoutesAndPoisStartShowing() {
		ClimbingGeometryBuilder.GeometrySpec crag = geometry(11, 16, "North Crag", 37);

		assertTrue(builder.buildHullLabelGeoJson(Arrays.asList(crag), 15.99)
				.contains("ctw-hull-count-37"));
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullLabelGeoJson(Arrays.asList(crag), 16));
	}

	private ClimbingGeometryBuilder.GeometrySpec geometry(float minZoom, float labelMaxZoom,
	                                                       String name, int elementCount) {
		return new ClimbingGeometryBuilder.GeometrySpec("key", Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25)), true, 0, 0, minZoom, 0,
				new MapCoordinate(45.5, 24.5), name, elementCount, labelMaxZoom);
	}
}
