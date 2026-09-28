package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class ClimbingRouteCounterTest {
	private final Map<Long, OsmNode> nodes = new HashMap<>();
	private final Map<Long, OsmCollectionEntity> collections = new HashMap<>();
	private final ClimbingRouteCounter counter = new ClimbingRouteCounter(
			new ClimbingRouteCounter.EntitySource() {
				@Override
				public Map<Long, OsmNode> loadNodes(List<Long> ids) {
					return select(nodes, ids);
				}

				@Override
				public Map<Long, OsmCollectionEntity> loadCollections(List<Long> ids) {
					return select(collections, ids);
				}
			});

	@Test
	public void countsNestedRoutesOncePerStyleAndIgnoresCycles() throws JSONException {
		addNode(10, route("climbing:sport", "yes", "climbing:trad", "yes"));
		addNode(11, route("climbing:sport", "yes", "climbing:ice", "no"));
		addNode(30, new JSONObject());
		addCollection(20, "way", route("climbing:boulder", "yes"), new JSONArray());
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"),
				members("node", 11, "way", 20, "relation", 1));
		crag.osmNodes = Arrays.asList(11L, 30L);
		OsmCollectionEntity area = addCollection(1, "relation", group("area"),
				members("node", 10, "relation", 2));
		area.osmNodes = Arrays.asList(10L, 11L, 30L);

		ClimbingRouteCounter.RouteSummary areaSummary = counter.summarize(area);
		assertEquals(3, areaSummary.getRouteCount());
		assertEquals(Integer.valueOf(2), areaSummary.getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(Integer.valueOf(1), areaSummary.getStyleCounts().get(GeoNode.ClimbingStyle.trad));
		assertEquals(Integer.valueOf(1), areaSummary.getStyleCounts().get(GeoNode.ClimbingStyle.boulder));
		assertFalse(areaSummary.getStyleCounts().containsKey(GeoNode.ClimbingStyle.ice));

		ClimbingRouteCounter.RouteSummary cragSummary = counter.summarize(crag);
		assertEquals(2, cragSummary.getRouteCount());
		assertEquals(Integer.valueOf(1), cragSummary.getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(Integer.valueOf(1), cragSummary.getStyleCounts().get(GeoNode.ClimbingStyle.boulder));
	}

	@Test
	public void routeNodeCountsItselfAndOtherNodesCountNothing() throws JSONException {
		OsmNode routeNode = addNode(10, route("climbing:sport", "yes"));
		OsmNode cragNode = addNode(11, group("crag").put("climbing:sport", "12"));

		assertEquals(1, counter.summarize(routeNode).getRouteCount());
		assertEquals(Integer.valueOf(1),
				counter.summarize(routeNode).getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(0, counter.summarize(cragNode).getRouteCount());
		assertEquals(0, counter.summarize(cragNode).getStyleCounts().size());
	}

	private static <T> Map<Long, T> select(Map<Long, T> source, List<Long> ids) {
		Map<Long, T> result = new HashMap<>();
		for (Long id : ids) {
			if (source.containsKey(id)) {
				result.put(id, source.get(id));
			}
		}
		return result;
	}

	private OsmNode addNode(long id, JSONObject tags) throws JSONException {
		OsmNode node = new OsmNode(new JSONObject()
				.put("type", "node").put("id", id).put("lat", 45.0).put("lon", 24.0)
				.put("tags", tags));
		nodes.put(id, node);
		return node;
	}

	private OsmCollectionEntity addCollection(long id, String type, JSONObject tags,
	                                          JSONArray members) throws JSONException {
		JSONObject json = new JSONObject().put("type", type).put("id", id).put("tags", tags);
		if ("relation".equals(type)) {
			json.put("members", members);
		}
		OsmCollectionEntity collection = new OsmCollectionEntity(json);
		collections.put(id, collection);
		return collection;
	}

	private static JSONObject route(String... styleTags) throws JSONException {
		JSONObject tags = new JSONObject().put("sport", "climbing").put("climbing", "route_bottom");
		for (int index = 0; index + 1 < styleTags.length; index += 2) {
			tags.put(styleTags[index], styleTags[index + 1]);
		}
		return tags;
	}

	private static JSONObject group(String climbingType) throws JSONException {
		return new JSONObject().put("sport", "climbing").put("climbing", climbingType);
	}

	private static JSONArray members(Object... typeAndRef) throws JSONException {
		JSONArray result = new JSONArray();
		for (int index = 0; index + 1 < typeAndRef.length; index += 2) {
			result.put(new JSONObject().put("type", typeAndRef[index])
					.put("ref", typeAndRef[index + 1]));
		}
		return result;
	}
}
