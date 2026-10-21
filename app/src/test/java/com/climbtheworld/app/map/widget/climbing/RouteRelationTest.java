package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class RouteRelationTest {
	private final Map<Long, OsmNode> nodes = new HashMap<>();

	@Test
	public void pinsTheRouteAtItsStartAndItsTopInItsGrade() throws Exception {
		OsmCollectionEntity relation = multiPitch(new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route_bottom"),
				new JSONObject()
						.put("climbing", "route_top")
						.put("natural", "tree"));

		RouteRelation route = RouteRelation.fromRelation(relation, nodes);

		// The start is the route_bottom, as the route.
		assertEquals(10L, route.start.getID());
		assertEquals("Flåklypa Grand Prix", route.start.getName());
		assertEquals(OsmEntity.EntityClimbingType.route, route.start.getNodeType());
		assertFalse(route.start.isRouteTop());
		assertEquals(nodes.get(10L).decimalLatitude, route.start.decimalLatitude, 0);
		// The top is the route_top, in the grade of the route.
		assertEquals(13L, route.top.getID());
		assertTrue(route.top.isRouteTop());
		assertEquals("5+", route.top.getTags().getString("climbing:grade:norwegian"));
		assertEquals(RouteWay.gradeColor(relation.getTags()), route.color);
		// Both leave the details to the relation, at the start.
		assertSame(route.route, route.start.route);
		assertSame(route.route, route.top.route);
		assertEquals("relation", route.route.jsonNodeInfo.getString("type"));
		assertEquals(21237508L, route.route.osmID);
		assertEquals(OsmEntity.EntityClimbingType.route, route.route.getNodeType());
		assertEquals(route.start.decimalLatitude, route.route.decimalLatitude, 0);
		assertEquals(route.start.decimalLongitude, route.route.decimalLongitude, 0);

		assertEquals(new HashSet<>(Arrays.asList(20L, 21L, 22L)), route.pitchIds);
		Map<Long, RouteRelation> byPitch = RouteRelation.byPitch(List.of(route));
		assertSame(route, byPitch.get(21L));
		assertNull(byPitch.get(10L));
	}

	@Test
	public void runsAlongItsMembersWhereItsEndsAreNotTagged() throws Exception {
		RouteRelation route = RouteRelation.fromRelation(
				multiPitch(new JSONObject(), new JSONObject()), nodes);
		assertEquals(10L, route.start.getID());
		assertEquals(13L, route.top.getID());

		// Listed from the top down, with only the top tagged.
		OsmCollectionEntity topDown = multiPitch(new JSONObject(),
				new JSONObject().put("climbing", "route_top"));
		Collections.reverse(topDown.osmNodes);
		route = RouteRelation.fromRelation(topDown, nodes);
		assertEquals(10L, route.start.getID());
		assertEquals(13L, route.top.getID());
		assertEquals(nodes.get(10L), RouteRelation.findStart(topDown, nodes));
	}

	@Test
	public void isNoneForOtherCollectionsOrWithoutTwoKnownEnds() throws Exception {
		OsmCollectionEntity relation = multiPitch(new JSONObject(), new JSONObject());

		JSONObject crag = new JSONObject(relation.jsonNodeInfo.toString());
		crag.getJSONObject("tags").put("climbing", "crag");
		assertNull(RouteRelation.fromRelation(new OsmCollectionEntity(crag), nodes));
		JSONObject way = new JSONObject(relation.jsonNodeInfo.toString()).put("type", "way");
		assertNull(RouteRelation.fromRelation(new OsmCollectionEntity(way), nodes));

		relation.osmNodes = Arrays.asList(10L, 99L);
		assertNull(RouteRelation.fromRelation(relation, nodes));
		assertNull(RouteRelation.findStart(relation, nodes));
	}

	/**
	 * A multi-pitch route as relation 21237508: the details on the relation, its pitches as
	 * climbing=route ways from its bottom (10), through bare belays (11, 12), to its top (13).
	 * Its nodes are those of its pitches in member order, as the importer resolves them.
	 */
	private OsmCollectionEntity multiPitch(JSONObject bottomTags, JSONObject topTags)
			throws Exception {
		node(10L, bottomTags);
		node(11L, new JSONObject().put("climbing:bolt", "anchor"));
		node(12L, new JSONObject().put("climbing:bolt", "anchor"));
		node(13L, topTags);
		OsmCollectionEntity relation = new OsmCollectionEntity(new JSONObject()
				.put("id", 21237508L)
				.put("type", "relation")
				.put("members", new JSONArray()
						.put(new JSONObject().put("type", "way").put("ref", 20L).put("role", ""))
						.put(new JSONObject().put("type", "way").put("ref", 21L).put("role", ""))
						.put(new JSONObject().put("type", "way").put("ref", 22L).put("role", "")))
				.put("tags", new JSONObject()
						.put("type", "route")
						.put("route", "climbing")
						.put("sport", "climbing")
						.put("climbing", "route")
						.put("climbing:pitches", "3")
						.put("climbing:grade:norwegian", "5+")
						.put("name", "Flåklypa Grand Prix")));
		relation.osmNodes = Arrays.asList(10L, 11L, 11L, 12L, 12L, 13L);
		return relation;
	}

	private void node(long id, JSONObject tags) throws Exception {
		nodes.put(id, new OsmNode(new JSONObject().put("type", "node").put("id", id)
				.put("lat", 60.452334 + id * 1e-4).put("lon", 9.3087097).put("tags", tags)));
	}
}
