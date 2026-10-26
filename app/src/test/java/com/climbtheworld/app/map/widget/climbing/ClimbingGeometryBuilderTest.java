package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
						.put("type", "site")
						.put("sport", "climbing")
						.put("climbing", "area")
						.put("name", "Grand Area")));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();

		builder.addGeometry(geometries, area, Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25)), 27, null);

		assertEquals(1, geometries.size());
		assertEquals(OsmEntity.EntityClimbingType.area,
				new GeoNode(new JSONObject(area.jsonNodeInfo.toString())).getNodeType());
		assertEquals(27, geometries.get(0).relationElementCount);
		assertTrue(geometries.get(0).getLabelImageId().endsWith("-27"));
	}

	@Test
	public void filteredRouteCountShowsEvenZeroOnTheLabelAndThePin() throws Exception {
		OsmCollectionEntity area = new OsmCollectionEntity(new JSONObject()
				.put("id", 458L)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("type", "site")
						.put("sport", "climbing")
						.put("climbing", "area")
						.put("name", "Grand Area")));
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		List<ClimbingGeometryBuilder.GeometrySpec> filtered = new ArrayList<>();
		List<ClimbingGeometryBuilder.GeometrySpec> unfiltered = new ArrayList<>();

		builder.addGeometry(filtered, area, triangle, 0, true, null);
		builder.addGeometry(unfiltered, area, triangle, 0, false, null);

		ClimbingGeometryBuilder.GeometrySpec geometry = filtered.get(0);
		assertTrue(geometry.routeCountFiltered);
		assertNotEquals(unfiltered.get(0).getLabelImageId(), geometry.getLabelImageId());
		GeoNode pin = geometry.pin(geometry.labelCoordinate);
		assertEquals("0", pin.getKey(ClimbingTags.KEY_ROUTES));
		GeoNode unfilteredPin = unfiltered.get(0).pin(unfiltered.get(0).labelCoordinate);
		assertEquals("", unfilteredPin.getKey(ClimbingTags.KEY_ROUTES));
	}

	@Test
	public void unnamedAreaHasNoLabelButKeepsItsRouteCountForItsPin() throws Exception {
		OsmCollectionEntity area = new OsmCollectionEntity(new JSONObject()
				.put("id", 457L)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("type", "site")
						.put("sport", "climbing")
						.put("climbing", "area")));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();

		builder.addGeometry(geometries, area, Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25)), 27, null);

		assertEquals(1, geometries.size());
		assertNull(geometries.get(0).labelCoordinate);
		assertEquals(27, geometries.get(0).relationElementCount);
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
		assertEquals(OsmEntity.EntityClimbingType.route,
				new GeoNode(new JSONObject(route.jsonNodeInfo.toString())).getNodeType());
		assertEquals(1, geometries.size());
		assertFalse(geometries.get(0).polygon);
		assertEquals(MapZoomLevels.POI_AND_ROUTE_MIN, geometries.get(0).minZoom, 0);
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN - 0.01,
						geometry -> false));
		assertTrue(builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN,
				geometry -> false).contains("LineString"));
	}

	@Test
	public void routeWayIsDrawnInTheColourOfItsGradeAsInTheArView() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmCollectionEntity way = routeWay(nodes);
		RouteWay route = ClimbingGeometryBuilder.toRouteWay(way, nodes);
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		builder.addGeometry(geometries, way, route.path, -1, route);
		builder.addGeometry(geometries, way(2L, new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre")), route.path);

		assertSame(route, geometries.get(0).route);
		String geoJson = builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN,
				geometry -> false);
		assertTrue(geoJson, geoJson.contains("{\"color\":\""
				+ rgba(ColorUtils.setAlphaComponent(route.color, DisplayableGeoNode.LINE_ALPHA))
				+ "\",\"casingColor\":\"" + rgba(DisplayableGeoNode.LINE_CASING_COLOR) + "\"}"));
		// Other ways stay grey, without the dark edge.
		assertTrue(geoJson, geoJson.contains("{\"color\":\"" + rgba(0xee3c3c3c) + "\"}"));
	}

	@Test
	public void ghostRouteWayFadesOutLikeItsPins() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmCollectionEntity way = routeWay(nodes);
		RouteWay route = ClimbingGeometryBuilder.toRouteWay(way, nodes);
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		builder.addGeometry(geometries, way, route.path, -1, route);
		builder.addGeometry(geometries, way(2L, new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre")), route.path);

		String geoJson = builder.buildWayGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN,
				geometry -> true);
		int hidden = DisplayableGeoNode.POI_ICON_ALPHA_HIDDEN;
		assertTrue(geoJson, geoJson.contains("{\"color\":\"" + rgba(ColorUtils.setAlphaComponent(
				route.color, DisplayableGeoNode.LINE_ALPHA * hidden / 255))
				+ "\",\"casingColor\":\"" + rgba(ColorUtils.setAlphaComponent(
				DisplayableGeoNode.LINE_CASING_COLOR,
				Color.alpha(DisplayableGeoNode.LINE_CASING_COLOR) * hidden / 255)) + "\"}"));
		// Only route lines fade with their pins.
		assertTrue(geoJson, geoJson.contains("{\"color\":\"" + rgba(0xee3c3c3c) + "\"}"));
	}

	@Test
	public void onlyClimbingRouteWaysAreRoutes() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmCollectionEntity route = routeWay(nodes);
		assertNotNull(ClimbingGeometryBuilder.toRouteWay(route, nodes));

		JSONObject crag = new JSONObject(route.jsonNodeInfo.toString());
		crag.getJSONObject("tags").put("climbing", "crag");
		assertNull(ClimbingGeometryBuilder.toRouteWay(new OsmCollectionEntity(crag), nodes));
		JSONObject relation = new JSONObject(route.jsonNodeInfo.toString()).put("type", "relation");
		assertNull(ClimbingGeometryBuilder.toRouteWay(new OsmCollectionEntity(relation), nodes));
	}

	@Test
	public void waysUsePoiZoomLimit() throws Exception {
		List<MapCoordinate> line = Arrays.asList(
				new MapCoordinate(45, 24), new MapCoordinate(46, 25));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		builder.addGeometry(geometries, way(1L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route")), line);
		builder.addGeometry(geometries, way(2L, new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre")), line);

		assertEquals(2, geometries.size());
		assertEquals(MapZoomLevels.POI_AND_ROUTE_MIN, geometries.get(0).minZoom, 0);
		assertEquals(MapZoomLevels.AREA_MIN, geometries.get(1).minZoom, 0);
	}

	@Test
	public void collectionsThatAreNoClimbingFeatureAreNotDrawn() throws Exception {
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		OsmCollectionEntity cliff = way(1L, new JSONObject()
				.put("sport", "climbing")
				.put("natural", "cliff"));
		OsmCollectionEntity cragWay = way(2L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "crag"));
		OsmCollectionEntity multipolygonCrag = relation(3L, new JSONObject()
				.put("type", "multipolygon")
				.put("sport", "climbing")
				.put("climbing", "crag"));
		for (OsmCollectionEntity collection : Arrays.asList(cliff, cragWay, multipolygonCrag)) {
			assertEquals(OsmEntity.EntityClimbingType.others, collection.entityClimbingType);
			builder.addGeometry(geometries, collection, triangle);
		}

		assertEquals(Collections.emptyList(), geometries);
	}

	@Test
	public void artificialHullUsesAreaZoomLimit() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		builder.addGeometry(geometries, gymRelation(1L), triangle);

		assertEquals(MapZoomLevels.AREA_MIN, geometries.get(0).minZoom, 0);
		assertEquals(EMPTY_FEATURE_COLLECTION,
				builder.buildHullGeoJson(geometries, MapZoomLevels.AREA_MIN - 0.01, false));
		assertTrue(builder.buildHullGeoJson(geometries, MapZoomLevels.AREA_MIN, false)
				.contains("Polygon"));
	}

	@Test
	public void cragWithOneRouteGetsHull() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();

		builder.addGeometry(geometries, namedCrag(), Collections.singletonList(
				new MapCoordinate(45, 24)), 1, null);

		assertEquals(1, geometries.size());
		assertTrue(geometries.get(0).polygon);
		assertTrue(geometries.get(0).coordinates.size() >= 3);
		assertEquals(45, geometries.get(0).labelCoordinate.getLatitude(), 1e-9);
		assertEquals(24, geometries.get(0).labelCoordinate.getLongitude(), 1e-9);
	}

	@Test
	public void cragWithTwoRoutesGetsHull() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();

		builder.addGeometry(geometries, namedCrag(), Arrays.asList(
				new MapCoordinate(45, 24), new MapCoordinate(45.001, 24.001)), 2, null);

		assertEquals(1, geometries.size());
		assertTrue(geometries.get(0).polygon);
		assertTrue(geometries.get(0).coordinates.size() >= 3);
		assertEquals(45.0005, geometries.get(0).labelCoordinate.getLatitude(), 1e-9);
		assertEquals(24.0005, geometries.get(0).labelCoordinate.getLongitude(), 1e-9);
		assertTrue(builder.buildHullGeoJson(geometries, MapZoomLevels.CRAG_MIN, true)
				.contains("LineString"));
	}

	@Test
	public void hullsAreEmittedAreaThenCrag() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		builder.addGeometry(geometries, relation(2L, "crag"), triangle);
		builder.addGeometry(geometries, relation(3L, "area"), triangle);

		String fills = builder.buildHullGeoJson(geometries, MapZoomLevels.POI_AND_ROUTE_MIN,
				false);

		int area =
				fills.indexOf(rgba(com.climbtheworld.app.map.DisplayableGeoNode.AREA_HULL_COLOR));
		int crag =
				fills.indexOf(rgba(com.climbtheworld.app.map.DisplayableGeoNode.CRAG_HULL_COLOR));
		assertTrue(area >= 0 && crag >= 0);
		assertTrue(area < crag);
	}

	@Test
	public void routeRelationsGetNoHull() throws Exception {
		List<ClimbingGeometryBuilder.GeometrySpec> geometries = new ArrayList<>();
		List<MapCoordinate> triangle = Arrays.asList(
				new MapCoordinate(45, 24),
				new MapCoordinate(46, 24),
				new MapCoordinate(45, 25));
		// A multi-pitch route with its pitches as members, as relation 21237508.
		OsmCollectionEntity multiPitch = new OsmCollectionEntity(new JSONObject()
				.put("id", 1L)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("type", "route")
						.put("route", "climbing")
						.put("sport", "climbing")
						.put("climbing", "route")
						.put("climbing:pitches", "7")
						.put("name", "Long Route")));
		builder.addGeometry(geometries, multiPitch, triangle);
		builder.addGeometry(geometries, relation(2L, "route"), triangle);
		// Even without climbing=route.
		builder.addGeometry(geometries, new OsmCollectionEntity(new JSONObject()
				.put("id", 3L)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("type", "route")
						.put("route", "climbing")
						.put("sport", "climbing"))), triangle);

		assertEquals(OsmEntity.EntityClimbingType.route, multiPitch.entityClimbingType);
		assertEquals(Collections.emptyList(), geometries);
		assertTrue(ClimbingGeometryBuilder.isRouteRelation(multiPitch));
		assertFalse(ClimbingGeometryBuilder.isRouteRelation(relation(4L, "crag")));
		// Its pitches, mapped as ways, are still drawn as routes.
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmCollectionEntity pitch = routeWay(nodes);
		assertFalse(ClimbingGeometryBuilder.isRouteRelation(pitch));
		builder.addGeometry(geometries, pitch, triangle);
		assertEquals(1, geometries.size());
		assertFalse(geometries.get(0).polygon);
	}

	@Test
	public void cragAndAreaHullsAreBuiltFromTheRouteStartsOnly() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		node(nodes, 1L, 45.1, 24, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom"));
		node(nodes, 2L, 45.2, 24, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route")
				.put("name", "Node Route"));
		node(nodes, 3L, 45.3, 24, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "boulder")
				.put("climbing:grade:font", "6a"));
		// Without sport=climbing, a route_bottom is still where a route starts.
		node(nodes, 4L, 45.4, 24, new JSONObject().put("climbing", "route_bottom"));
		// Left out: a top, a belay, and an outline of the rock.
		node(nodes, 5L, 45.5, 24, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_top"));
		node(nodes, 6L, 45.6, 24, new JSONObject().put("climbing:bolt", "anchor"));
		node(nodes, 7L, 45.7, 24, new JSONObject());
		List<Long> all = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L);
		List<MapCoordinate> starts = Arrays.asList(new MapCoordinate(45.1, 24),
				new MapCoordinate(45.2, 24), new MapCoordinate(45.3, 24),
				new MapCoordinate(45.4, 24));

		for (String type : Arrays.asList("crag", "area")) {
			OsmCollectionEntity relation = relation(1L, type);
			relation.osmNodes = all;
			assertEquals(type, starts, ClimbingGeometryBuilder.toCoordinates(relation, nodes));
			// Without any route start, there is no hull.
			relation.osmNodes = Arrays.asList(5L, 6L, 7L);
			assertTrue(type, ClimbingGeometryBuilder.toCoordinates(relation, nodes).isEmpty());
		}
		// Other relations, and ways, are drawn through all their nodes.
		OsmCollectionEntity gym = gymRelation(2L);
		gym.osmNodes = all;
		assertEquals(7, ClimbingGeometryBuilder.toCoordinates(gym, nodes).size());
		OsmCollectionEntity gymWay = way(3L, new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre"));
		gymWay.osmNodes = all;
		assertEquals(7, ClimbingGeometryBuilder.toCoordinates(gymWay, nodes).size());
	}

	private OsmCollectionEntity relation(long id, JSONObject tags) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "relation")
				.put("tags", tags));
	}

	/** A gym mapped as a multipolygon. */
	private OsmCollectionEntity gymRelation(long id) throws Exception {
		return relation(id, new JSONObject()
				.put("type", "multipolygon")
				.put("sport", "climbing")
				.put("leisure", "sports_centre"));
	}

	private OsmCollectionEntity relation(long id, String climbingType) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("type", "site")
						.put("sport", "climbing")
						.put("climbing", climbingType)));
	}

	private OsmCollectionEntity namedCrag() throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", 789L)
				.put("type", "relation")
				.put("tags", new JSONObject()
						.put("type", "site")
						.put("sport", "climbing")
						.put("climbing", "crag")
						.put("name", "Small Crag")));
	}

	// A multi-pitch route: the details on the bottom, a bare top, and a bare way between them.
	private OsmCollectionEntity routeWay(Map<Long, OsmNode> nodes) throws Exception {
		OsmNode bottom = node(nodes, 11L, 46.2259683, -73.7403424, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom")
				.put("name", "Long Route")
				.put("climbing:grade:uiaa", "6+"));
		OsmNode top = node(nodes, 12L, 46.2260824, -73.7403542, new JSONObject());
		return new OsmCollectionEntity(new JSONObject()
				.put("id", 1L)
				.put("type", "way")
				.put("nodes", new JSONArray().put(bottom.osmID).put(top.osmID))
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route")
						.put("climbing:pitches", "3")));
	}

	private static OsmNode node(Map<Long, OsmNode> nodes, long id, double latitude,
	                            double longitude, JSONObject tags) throws Exception {
		OsmNode node = new OsmNode(new JSONObject().put("type", "node").put("id", id)
				.put("lat", latitude).put("lon", longitude).put("tags", tags));
		nodes.put(id, node);
		return node;
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
				new MapCoordinate(45.5, 24.5), name, elementCount, false, labelMaxZoom, null, null);
	}
}
