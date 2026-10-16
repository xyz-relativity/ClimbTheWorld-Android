package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class RouteLineTest {
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
		RouteLine route = RouteLine.fromWay(way(routeWayTags(), bottom, top), nodes);
		assertEquals(bottom.osmID, route.bottom.getID());
		assertEquals(top.osmID, route.top.getID());

		// Drawn from the top, the route_top tag tells.
		RouteLine reversed = RouteLine.fromWay(way(routeWayTags(), top, bottom), nodes);
		assertEquals(bottom.osmID, reversed.bottom.getID());
		assertEquals(top.osmID, reversed.top.getID());
	}

	@Test
	public void topTakesTheGradeOfTheRoute() throws JSONException {
		OsmNode bottom = bottom();
		RouteLine route = RouteLine.fromWay(way(routeWayTags(), bottom, top()), nodes);
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
		RouteLine route = RouteLine.fromWay(
				way(routeWayTags().put("climbing:grade:uiaa", "7"), bottom, top()), nodes);
		int levelId = route.top.getLevelId(ClimbingTags.KEY_GRADE_TAG);
		assertTrue(levelId >= 0);
		assertEquals(Globals.gradeToColorState(levelId).getDefaultColor(), route.color);
	}

	@Test
	public void untaggedTopIsTheEndOfTheWay() throws JSONException {
		OsmNode bottom = bottom();
		OsmNode end = node(2, 46.2260824, -73.7403542, new JSONObject());
		RouteLine route = RouteLine.fromWay(way(routeWayTags(), bottom, end), nodes);
		assertEquals(end.osmID, route.top.getID());
		assertEquals(GeoNode.NodeTypes.route, route.top.getNodeType());
		assertTrue(route.top.isRouteTop());
	}

	@Test
	public void needsTwoKnownNodes() throws JSONException {
		OsmNode bottom = bottom();
		OsmCollectionEntity way = way(routeWayTags(), bottom, top());
		nodes.remove(14288355603L);
		assertNull(RouteLine.fromWay(way, nodes));
	}

	@Test
	public void lineClimbsFromTheFootOfTheWallToTheTop() throws JSONException {
		RouteLine route = RouteLine.fromWay(way(routeWayTags(), bottom(), top()), nodes);
		// The ground rises 10 m for every 10 m north.
		route.build(LATITUDE, LONGITUDE, (latitude, longitude, distance) ->
				300 + (latitude - LATITUDE) * ClimbingHull.METERS_PER_DEGREE, 295);
		ClimbingHull.GroundLine line = route.getLine();
		assertTrue(line.size > 2);
		assertEquals(0, line.east[0], 1e-9);
		assertEquals(0, line.north[0], 1e-9);
		assertEquals("Where the bottom pin stands", 295, line.elevation[0], 1e-9);
		int last = line.size - 1;
		double topNorth = (46.2260824 - LATITUDE) * ClimbingHull.METERS_PER_DEGREE;
		assertEquals(topNorth, line.north[last], 1e-6);
		assertEquals("On the ground at the top", 300 + topNorth, line.elevation[last], 1e-6);
	}

	@Test
	public void lineStartsOnTheGroundUntilTheFootIsKnown() throws JSONException {
		RouteLine route = RouteLine.fromWay(way(routeWayTags(), bottom(), top()), nodes);
		route.build(LATITUDE, LONGITUDE, (latitude, longitude, distance) -> 300, Double.NaN);
		assertEquals(300, route.getLine().elevation[0], 1e-9);
	}

	@Test
	public void routesGoneAreGiven() throws JSONException {
		OsmNode bottom = bottom();
		RouteLine kept = RouteLine.fromWay(way(routeWayTags(), bottom, top()), nodes);
		RouteLine gone = RouteLine.fromWay(way(routeWayTags(), bottom,
				node(3, 46.226, -73.7403, new JSONObject())), nodes);
		RouteLines routeLines = new RouteLines();
		assertTrue(routeLines.setRoutes(Arrays.asList(kept, gone)).isEmpty());
		assertTrue(routeLines.isTop(3));

		RouteLine reloaded = RouteLine.fromWay(way(routeWayTags(), bottom, top()), nodes);
		List<RouteLine> removed = routeLines.setRoutes(Collections.singletonList(reloaded));
		assertEquals(Collections.singletonList(gone), removed);
		assertTrue(routeLines.isTop(14288355603L));
		assertFalse(routeLines.isTop(3));
		assertFalse(routeLines.isTop(bottom.osmID));
	}

	@Test
	public void laidOutAroundTheObserverOnce() throws JSONException {
		RouteLine route = RouteLine.fromWay(way(routeWayTags(), bottom(), top()), nodes);
		RouteLines routeLines = new RouteLines();
		routeLines.setRoutes(Collections.singletonList(route));
		int[] bottomLookups = {0};
		RouteLines.BottomElevation bottoms = (node, distance) -> {
			bottomLookups[0]++;
			return 295;
		};
		routeLines.update(LATITUDE, LONGITUDE, (latitude, longitude, distance) -> 300, bottoms,
				0, 0);
		assertEquals(1, bottomLookups[0]);
		assertEquals(295, route.getLine().elevation[0], 1e-9);

		// Still, it is left as is.
		routeLines.update(LATITUDE, LONGITUDE, (latitude, longitude, distance) -> 300, bottoms,
				0, 0);
		assertEquals(1, bottomLookups[0]);

		// The terrain loaded more.
		routeLines.update(LATITUDE, LONGITUDE, (latitude, longitude, distance) -> 300, bottoms,
				1, ClimbingHulls.TERRAIN_REBUILD_DELAY_MS);
		assertEquals(2, bottomLookups[0]);
	}
}
