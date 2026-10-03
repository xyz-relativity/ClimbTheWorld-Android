package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

		String labels = builder.buildHullLabelGeoJson(Collections.singletonList(area), 10.5);

		assertTrue(labels.contains("\"labelKey\":\"key\""));
		assertTrue(labels.contains("\"name\":\"Grand \\\"Wall\\\"\""));
		assertTrue(labels.contains("\"labelIcon\":\"ctw-hull-label-key-12\""));
		assertTrue(labels.contains("\"coordinates\":[24.5,45.5]"));
	}

	@Test
	public void areaLabelIsHiddenWhenCragsStartShowing() {
		ClimbingGeometryBuilder.GeometrySpec area = geometry(9, 11, "Grand Wall", 12);

		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullLabelGeoJson(Collections.singletonList(area), 11));
	}

	@Test
	public void cragLabelIsHiddenWhenRoutesAndPoisStartShowing() {
		ClimbingGeometryBuilder.GeometrySpec crag = geometry(11, 16, "North Crag", 37);

		assertTrue(builder.buildHullLabelGeoJson(Collections.singletonList(crag), 15.99)
				.contains("ctw-hull-label-key"));
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullLabelGeoJson(Collections.singletonList(crag), 16));
	}

	@Test
	public void areaLabelUsesTotalRouteCountForPoiIcon() throws Exception {
		OsmCollectionEntity area = new OsmCollectionEntity(new JSONObject()
				.put("id", 456L)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "area")
						.put("name", "Grand Area")));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();

		builder.addGeometry(geometries, area, Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25)), 27);

		assertEquals(1, geometries.size());
		assertEquals(GeoNode.NodeTypes.area,
				new GeoNode(new JSONObject(area.jsonNodeInfo.toString())).getNodeType());
		assertEquals(27, geometries.get(0).relationElementCount);
		assertTrue(geometries.get(0).getLabelImageId().endsWith("-27"));
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
		assertEquals(GeoNode.NodeTypes.route,
				new GeoNode(new JSONObject(route.jsonNodeInfo.toString())).getNodeType());
		assertEquals(1, geometries.size());
		assertFalse(geometries.get(0).polygon);
		assertEquals(MapZoomLevels.POI_AND_ROUTE_MIN, geometries.get(0).minZoom, 0);
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN - 0.01));
		assertTrue(builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN)
				.contains("LineString"));
	}

	@Test
	public void waysUsePoiZoomLimit() throws Exception {
		List<MapCoordinate> line = Arrays.asList(
				new MapCoordinate(45, 24), new MapCoordinate(46, 25));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		builder.addGeometry(geometries, way(1L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "crag")), line);
		builder.addGeometry(geometries, way(2L, new JSONObject()
				.put("sport", "climbing")), line);
		builder.addGeometry(geometries, way(3L, new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre")), line);

		assertEquals(3, geometries.size());
		assertEquals(MapZoomLevels.POI_AND_ROUTE_MIN, geometries.get(0).minZoom, 0);
		assertEquals(MapZoomLevels.POI_AND_ROUTE_MIN, geometries.get(1).minZoom, 0);
		assertEquals(MapZoomLevels.AREA_MIN, geometries.get(2).minZoom, 0);
	}

	@Test
	public void artificialHullUsesAreaZoomLimit() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		builder.addGeometry(geometries, relation(1L, "artificial"), triangle);

		assertEquals(MapZoomLevels.AREA_MIN, geometries.get(0).minZoom, 0);
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullGeoJson(geometries, MapZoomLevels.AREA_MIN - 0.01, false));
		assertTrue(builder.buildHullGeoJson(geometries, MapZoomLevels.AREA_MIN, false)
				.contains("Polygon"));
	}

	@Test
	public void hullsAreEmittedAreaThenCragThenRoute() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		builder.addGeometry(geometries, relation(1L, "route"), triangle);
		builder.addGeometry(geometries, relation(2L, "crag"), triangle);
		builder.addGeometry(geometries, relation(3L, "area"), triangle);

		String fills = builder.buildHullGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN, false);

		int area = fills.indexOf(rgba(com.climbtheworld.app.map.DisplayableGeoNode.AREA_HULL_COLOR));
		int crag = fills.indexOf(rgba(com.climbtheworld.app.map.DisplayableGeoNode.CRAG_HULL_COLOR));
		int route = fills.indexOf(rgba(0xaaff0000));
		assertTrue(area >= 0 && crag >= 0 && route >= 0);
		assertTrue(area < crag);
		assertTrue(crag < route);
	}

	private OsmCollectionEntity relation(long id, String climbingType) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", climbingType)));
	}

	private OsmCollectionEntity way(long id, JSONObject tags) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "way")
				.put("tags", tags));
	}

	private String rgba(int color) {
		return "rgba(" + (color >> 16 & 0xff) + "," + (color >> 8 & 0xff) + ","
				+ (color & 0xff) + "," + (color >>> 24) / 255.0 + ")";
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
