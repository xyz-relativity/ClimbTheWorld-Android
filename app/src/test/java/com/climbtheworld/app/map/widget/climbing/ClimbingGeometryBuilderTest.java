package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ClimbingGeometryBuilderTest {
	private static final String EMPTY_FEATURE_COLLECTION =
			"{\"type\":\"FeatureCollection\",\"features\":[]}";
	private final ClimbingGeometryBuilder builder = new ClimbingGeometryBuilder();

	@Test
	public void areaLabelContainsEscapedNameAndRelationLabelIconBelowCragZoom() {
		ClimbingGeometryBuilder.GeometrySpec area = geometry(
				9, 11, "Grand \"Wall\"", 12);

		String labels = builder.buildHullLabelGeoJson(Arrays.asList(area), 10.5);

		assertTrue(labels.contains("\"labelKey\":\"key\""));
		assertTrue(labels.contains("\"name\":\"Grand \\\"Wall\\\"\""));
		assertTrue(labels.contains("\"labelIcon\":\"ctw-hull-label-key\""));
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
				.contains("ctw-hull-label-key"));
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullLabelGeoJson(Arrays.asList(crag), 16));
	}

	@Test
	public void climbingRouteWayUsesRouteLineZoomLimit() throws Exception {
		OsmCollectionEntity route = new OsmCollectionEntity(new JSONObject()
				.put("id", 123L)
				.put("type", "way")
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route")));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();

		builder.addGeometry(geometries, route, Arrays.asList(
				new MapCoordinate(45, 24), new MapCoordinate(46, 25)));

		assertEquals(OsmEntity.EntityClimbingType.route, route.entityClimbingType);
		assertEquals(1, geometries.size());
		assertFalse(geometries.get(0).polygon);
		assertEquals(MapZoomLevels.POI_AND_ROUTE_MIN, geometries.get(0).minZoom, 0);
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN - 0.01));
		assertTrue(builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN)
				.contains("LineString"));
	}

	private ClimbingGeometryBuilder.GeometrySpec geometry(float minZoom, float labelMaxZoom,
	                                                       String name, int elementCount) {
		return new ClimbingGeometryBuilder.GeometrySpec("key", Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25)), true, 0, 0, minZoom, 0,
				new MapCoordinate(45.5, 24.5), name, elementCount, labelMaxZoom, null);
	}
}
