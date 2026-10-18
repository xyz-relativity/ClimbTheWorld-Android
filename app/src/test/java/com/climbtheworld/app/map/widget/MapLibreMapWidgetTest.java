package com.climbtheworld.app.map.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.map.widget.climbing.RouteWay;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class MapLibreMapWidgetTest {
	@Test
	public void artificialPoiIsVisibleAtAreaZoom() throws Exception {
		GeoNode gym = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre"));
		GeoNode buildingGym = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("building", "yes"));
		GeoNode area = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "area"));

		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(gym, MapZoomLevels.AREA_MIN));
		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(buildingGym, MapZoomLevels.AREA_MIN));
		assertFalse(MapLibreMapWidget.isPoiVisibleAtZoom(area, MapZoomLevels.AREA_MIN));
		assertFalse(MapLibreMapWidget.isPoiVisibleAtZoom(gym,
				MapZoomLevels.AREA_MIN - 0.01));
	}

	@Test
	public void allPoisAreVisibleAtTheRegularPoiZoom() throws Exception {
		GeoNode area = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "area"));

		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(area,
				MapZoomLevels.POI_AND_ROUTE_MIN));
	}

	@Test
	public void headingAccuracyWedgeRoundsUpToWholeSteps() {
		assertEquals(5, MapLibreMapWidget.headingAccuracyWedgeDegrees(0.5));
		assertEquals(5, MapLibreMapWidget.headingAccuracyWedgeDegrees(5));
		assertEquals(10, MapLibreMapWidget.headingAccuracyWedgeDegrees(5.1));
		// Beyond half a turn, every way is as likely: a full circle.
		assertEquals(180, MapLibreMapWidget.headingAccuracyWedgeDegrees(400));
	}

	@Test
	public void noHeadingAccuracyWedgeWithoutAnAccuracy() {
		assertEquals(0, MapLibreMapWidget.headingAccuracyWedgeDegrees(Double.NaN));
		assertEquals(0, MapLibreMapWidget.headingAccuracyWedgeDegrees(0));
	}

	@Test
	public void theSamePoiKeepsItsPin() throws Exception {
		assertEquals(poiIconKey("route_bottom"), poiIconKey("route_bottom"));
	}

	@Test
	public void aRouteTopGetsItsOwnPin() throws Exception {
		// The same node once edited into the top of its route, which has the shorter pin.
		assertNotEquals(poiIconKey("route_bottom"), poiIconKey("route_top"));
	}

	@Test
	public void routeTopIsPinnedWhereverItsBottomIs() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmNode bottom = node(nodes, 11L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom")
				.put("name", "Sport 400"));
		OsmNode top = node(nodes, 12L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_top"));
		RouteWay route = RouteWay.fromWay(routeWay(bottom, top), nodes);

		// Looking at the top of a 400 m route, its bottom is beyond the loaded area.
		Map<Long, DisplayableGeoNode> pois = new HashMap<>();
		MapLibreMapWidget.addRouteTop(pois, route);
		assertSame(route.top, pois.get(top.osmID).geoNode);
	}

	@Test
	public void noTopWithoutAPinAtTheBottom() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmNode bottom = node(nodes, 11L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom"));
		OsmNode top = node(nodes, 12L, new JSONObject());
		RouteWay route = RouteWay.fromWay(routeWay(bottom, top), nodes);

		Map<Long, DisplayableGeoNode> pois = new HashMap<>();
		MapLibreMapWidget.addRouteTop(pois, route);
		assertTrue(pois.isEmpty());
	}

	@Test
	public void routeTopStandsInForTheRouteTopPin() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmNode bottom = node(nodes, 11L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom")
				.put("name", "Long Route"));
		OsmNode top = node(nodes, 12L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_top")
				.put("name", "Top"));
		RouteWay route = RouteWay.fromWay(routeWay(bottom, top), nodes);
		Map<Long, DisplayableGeoNode> pois = new HashMap<>();
		pois.put(bottom.osmID, DataManager.toDisplayableNode(bottom));
		pois.put(top.osmID, DataManager.toDisplayableNode(top));

		MapLibreMapWidget.addRouteTop(pois, route);
		assertSame(route.top, pois.get(top.osmID).geoNode);
	}

	@Test
	public void bottomOfTheNextPitchKeepsItsPin() throws Exception {
		Map<Long, OsmNode> nodes = new HashMap<>();
		OsmNode bottom = node(nodes, 11L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom")
				.put("name", "Pitch 1"));
		OsmNode belay = node(nodes, 12L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom")
				.put("name", "Pitch 2"));
		RouteWay route = RouteWay.fromWay(routeWay(bottom, belay), nodes);
		Map<Long, DisplayableGeoNode> pois = new HashMap<>();
		pois.put(bottom.osmID, DataManager.toDisplayableNode(bottom));
		DisplayableGeoNode nextPitch = DataManager.toDisplayableNode(belay);
		pois.put(belay.osmID, nextPitch);

		MapLibreMapWidget.addRouteTop(pois, route);
		assertSame(nextPitch, pois.get(belay.osmID));
	}

	private static OsmNode node(Map<Long, OsmNode> nodes, long id, JSONObject tags)
			throws Exception {
		OsmNode node = new OsmNode(new JSONObject().put("type", "node").put("id", id)
				.put("lat", 46.2259683 + id * 1e-5).put("lon", -73.7403424).put("tags", tags));
		nodes.put(id, node);
		return node;
	}

	private static OsmCollectionEntity routeWay(OsmNode bottom, OsmNode top) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", 1L)
				.put("type", "way")
				.put("nodes", new JSONArray().put(bottom.osmID).put(top.osmID))
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route")));
	}

	private String poiIconKey(String climbing) throws Exception {
		return MapLibreMapWidget.poiIconKey(new DisplayableGeoNode(nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", climbing))));
	}

	private GeoNode nodeWithTags(JSONObject tags) throws Exception {
		return new GeoNode(new JSONObject()
				.put("id", 1L)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", tags));
	}
}
