package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class RouteWayTest {
	private static final double LATITUDE = 46.2259683;
	private static final double LONGITUDE = -73.7403424;
	private final Map<Long, OsmNode> nodes = new HashMap<>();

	// Granny Z, as mapped: the details on the bottom, a bare top, and a bare way between them.
	private OsmNode bottom() throws JSONException {
		return node(9974699415L, LATITUDE, LONGITUDE, new JSONObject()
				.put("climbing", "route_bottom").put("sport", "climbing").put("name", "Granny Z")
				.put("climbing:grade:yds_class", "5.7").put("climbing:bolts", "8"));
	}

	private OsmNode top() throws JSONException {
		return node(14288355603L, 46.2260824, -73.7403542, new JSONObject()
				.put("climbing", "route_top").put("sport", "climbing"));
	}

	private OsmNode node(long id, double latitude, double longitude, JSONObject tags)
			throws JSONException {
		OsmNode node = new OsmNode(new JSONObject().put("type", "node").put("id", id)
				.put("lat", latitude).put("lon", longitude).put("tags", tags));
		nodes.put(id, node);
		return node;
	}

	private static OsmCollectionEntity way(JSONObject tags, OsmNode... path)
			throws JSONException {
		JSONArray ids = new JSONArray();
		for (OsmNode node : path) {
			ids.put(node.osmID);
		}
		return new OsmCollectionEntity(new JSONObject().put("type", "way").put("id", 1568644445L)
				.put("nodes", ids).put("tags", tags));
	}

	private static JSONObject routeWayTags() throws JSONException {
		return new JSONObject().put("climbing", "route").put("sport", "climbing");
	}

	@Test
	public void runsFromTheBottomToTheTop() throws JSONException {
		OsmNode bottom = bottom();
		OsmNode top = top();
		RouteWay route = RouteWay.fromWay(way(routeWayTags(), bottom, top), nodes);
		assertEquals(bottom.osmID, route.bottom.getID());
		assertEquals(top.osmID, route.top.getID());

		// Drawn from the top, the route_top tag tells.
		RouteWay reversed = RouteWay.fromWay(way(routeWayTags(), top, bottom), nodes);
		assertEquals(bottom.osmID, reversed.bottom.getID());
		assertEquals(top.osmID, reversed.top.getID());
		assertEquals(Arrays.asList(
				new MapCoordinate(bottom.decimalLatitude, bottom.decimalLongitude),
				new MapCoordinate(top.decimalLatitude, top.decimalLongitude)), reversed.path);
	}

	@Test
	public void topTakesTheGradeOfTheRoute() throws JSONException {
		OsmNode bottom = bottom();
		RouteWay route = RouteWay.fromWay(way(routeWayTags(), bottom, top()), nodes);
		int levelId = GeoNode.getLevelId(bottom.getTags(), ClimbingTags.KEY_GRADE_TAG);
		assertTrue(levelId >= 0);

		assertTrue(route.top.isRouteTop());
		assertEquals(levelId, route.top.getLevelId(ClimbingTags.KEY_GRADE_TAG));
		assertEquals("Only the grade", "", route.top.getName());
		assertFalse(route.top.getTags().has("climbing:bolts"));
		assertEquals(Globals.gradeToColorState(levelId).getDefaultColor(), route.color);
		assertSame("A tap shows the route", route.bottom, route.top.route);
	}

	@Test
	public void gradeOnTheWayWhenNotOnTheBottom() throws JSONException {
		OsmNode bottom = node(1, LATITUDE, LONGITUDE, new JSONObject()
				.put("climbing", "route_bottom").put("sport", "climbing").put("name", "Route"));
		RouteWay route = RouteWay.fromWay(
				way(routeWayTags().put("climbing:grade:uiaa", "7"), bottom, top()), nodes);
		int levelId = route.top.getLevelId(ClimbingTags.KEY_GRADE_TAG);
		assertTrue(levelId >= 0);
		assertEquals(Globals.gradeToColorState(levelId).getDefaultColor(), route.color);
	}

	@Test
	public void untaggedTopIsTheEndOfTheWay() throws JSONException {
		OsmNode bottom = bottom();
		OsmNode end = node(2, 46.2260824, -73.7403542, new JSONObject());
		RouteWay route = RouteWay.fromWay(way(routeWayTags(), bottom, end), nodes);
		assertEquals(end.osmID, route.top.getID());
		assertEquals(GeoNode.NodeTypes.route, route.top.getNodeType());
		assertTrue(route.top.isRouteTop());
	}

	@Test
	public void bottomWithAPinOfItsOwn() throws JSONException {
		assertTrue(RouteWay.fromWay(way(routeWayTags(), bottom(), top()), nodes).bottomPinned);

		OsmNode unnamed = node(1, LATITUDE, LONGITUDE, new JSONObject()
				.put("climbing", "route_bottom").put("sport", "climbing"));
		assertFalse(RouteWay.fromWay(way(routeWayTags(), unnamed, top()), nodes).bottomPinned);
	}

	@Test
	public void needsTwoKnownNodes() throws JSONException {
		OsmNode bottom = bottom();
		OsmCollectionEntity way = way(routeWayTags(), bottom, top());
		nodes.remove(14288355603L);
		assertNull(RouteWay.fromWay(way, nodes));
	}
}
