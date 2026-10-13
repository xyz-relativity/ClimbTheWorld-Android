package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

@RunWith(RobolectricTestRunner.class)
public class ClimbingRouteCounterTest {
	private final Map<Long, OsmNode> nodes = new HashMap<>();
	private final Map<String, OsmCollectionEntity> collections = new HashMap<>();
	private final ClimbingRouteCounter counter = new ClimbingRouteCounter(
			new ClimbingRouteCounter.EntitySource() {
				@Override
				public Map<Long, OsmNode> loadNodes(List<Long> ids) {
					return select(nodes, ids);
				}

				@Override
				public Map<String, OsmCollectionEntity> loadCollections(List<Long> ids) {
					return selectCollections(ids);
				}
			});

	private static <T> Map<Long, T> select(Map<Long, T> source, List<Long> ids) {
		Map<Long, T> result = new HashMap<>();
		for (Long id : ids) {
			if (source.containsKey(id)) {
				result.put(id, source.get(id));
			}
		}
		return result;
	}

	private static JSONObject route(String... styleTags) throws JSONException {
		JSONObject tags = new JSONObject().put("sport", "climbing").put("climbing",
				"route_bottom");
		for (int index = 0; index + 1 < styleTags.length; index += 2) {
			tags.put(styleTags[index], styleTags[index + 1]);
		}
		return tags;
	}

	private static JSONObject routeTop(String... styleTags) throws JSONException {
		return route(styleTags).put("climbing", "route_top");
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

	@Test
	public void countsNestedRoutesOncePerStyleAndIgnoresCycles() throws JSONException {
		addNode(10, route("climbing:sport", "yes", "climbing:trad", "yes"));
		addNode(11, route("climbing:sport", "yes", "climbing:ice", "no"));
		addNode(30, new JSONObject());
		addCollection(20, "way", route("climbing:boulder", "yes"), new JSONArray());
		addCollection(20, "relation", group("crag"), new JSONArray());
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"),
				members("node", 11, "way", 20, "relation", 1));
		crag.osmNodes = Arrays.asList(11L, 30L);
		OsmCollectionEntity area = addCollection(1, "relation", group("area"),
				members("node", 10, "relation", 2));
		area.osmNodes = Arrays.asList(10L, 11L, 30L);

		ClimbingRouteCounter.RouteSummary areaSummary = counter.summarize(area);
		assertEquals(3, areaSummary.getRouteCount());
		assertEquals(Integer.valueOf(2),
				areaSummary.getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(Integer.valueOf(1),
				areaSummary.getStyleCounts().get(GeoNode.ClimbingStyle.trad));
		assertEquals(Integer.valueOf(1),
				areaSummary.getStyleCounts().get(GeoNode.ClimbingStyle.boulder));
		assertFalse(areaSummary.getStyleCounts().containsKey(GeoNode.ClimbingStyle.ice));

		ClimbingRouteCounter.RouteSummary cragSummary = counter.summarize(crag);
		assertEquals(2, cragSummary.getRouteCount());
		assertEquals(Integer.valueOf(1),
				cragSummary.getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(Integer.valueOf(1),
				cragSummary.getStyleCounts().get(GeoNode.ClimbingStyle.boulder));
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

	@Test
	public void groupsStyleRoutesByGradeEasiestFirstWithUnknownLast() throws JSONException {
		String hard = GradeSystem.uiaa.getGrade(20);
		String easy = GradeSystem.uiaa.getGrade(10);
		int hardIndex = GradeSystem.uiaa.indexOf(hard);
		int easyIndex = GradeSystem.uiaa.indexOf(easy);
		addNode(10, route("climbing:sport", "yes", "climbing:grade:uiaa", hard));
		addNode(11, route("climbing:sport", "yes", "climbing:grade:uiaa", easy));
		addNode(12, route("climbing:sport", "yes", "climbing:grade:uiaa", hard));
		addNode(13, route("climbing:sport", "yes"));
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"), new JSONArray());
		crag.osmNodes = Arrays.asList(10L, 11L, 12L, 13L);

		SortedMap<Integer, Integer> grades =
				counter.summarize(crag).getGradeCounts(GeoNode.ClimbingStyle.sport);
		assertEquals(Arrays.asList(easyIndex, hardIndex, ClimbingRouteCounter.UNKNOWN_GRADE),
				new ArrayList<>(grades.keySet()));
		assertEquals(Integer.valueOf(1), grades.get(easyIndex));
		assertEquals(Integer.valueOf(2), grades.get(hardIndex));
		assertEquals(Integer.valueOf(1), grades.get(ClimbingRouteCounter.UNKNOWN_GRADE));
		assertEquals(easyIndex, counter.summarize(crag).getMinGrade());
		assertEquals(hardIndex, counter.summarize(crag).getMaxGrade());
		assertTrue(counter.summarize(crag).getGradeCounts(GeoNode.ClimbingStyle.trad).isEmpty());
	}

	@Test
	public void tracksShortestAndLongestRouteLength() throws JSONException {
		addNode(10, route("climbing:sport", "yes", "climbing:length", "25"));
		addNode(11, route("climbing:sport", "yes", "climbing:length", "12.5 m"));
		addNode(12, route("climbing:sport", "yes", "climbing:length", "40 ft"));
		addNode(13, route("climbing:sport", "yes"));
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"), new JSONArray());
		crag.osmNodes = Arrays.asList(10L, 11L, 12L, 13L);

		ClimbingRouteCounter.RouteSummary summary = counter.summarize(crag);
		assertEquals(12.5, summary.getMinLength(), 0);
		assertEquals(25, summary.getMaxLength(), 0);

		OsmCollectionEntity empty = addCollection(3, "relation", group("crag"), new JSONArray());
		empty.osmNodes = List.of(13L);
		assertTrue(Double.isNaN(counter.summarize(empty).getMinLength()));
		assertTrue(Double.isNaN(counter.summarize(empty).getMaxLength()));
		assertEquals(ClimbingRouteCounter.UNKNOWN_GRADE, counter.summarize(empty).getMinGrade());
		assertEquals(ClimbingRouteCounter.UNKNOWN_GRADE, counter.summarize(empty).getMaxGrade());
	}

	@Test
	public void keepsTheNamesOfNamedRoutesIncludingNestedOnes() throws JSONException {
		addNode(10, route("climbing:sport", "yes", "name", "Été indien"));
		addNode(11, route("climbing:sport", "yes"));
		addNode(12, group("crag").put("name", "Not a route"));
		addCollection(20, "way", route("climbing:trad", "yes", "name", " Fissure "),
				new JSONArray());
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"),
				members("node", 10, "node", 11, "node", 12, "way", 20));
		crag.osmNodes = Arrays.asList(10L, 11L, 12L);

		assertEquals(new HashSet<>(Arrays.asList("Été indien", "Fissure")),
				new HashSet<>(counter.summarize(crag).getRouteNames()));
		assertTrue(counter.summarize(nodes.get(11L)).getRouteNames().isEmpty());
	}

	@Test
	public void countsARouteWayAndItsTaggedEndsOnce() throws JSONException {
		addNode(10, route("climbing:sport", "yes", "name", "Dharma"));
		addNode(11, routeTop("climbing:sport", "yes", "name", "Dharma"));
		addNode(12, new JSONObject());
		OsmCollectionEntity way = addCollection(20, "way",
				route("climbing:sport", "yes", "name", "Dharma").put("climbing", "route"),
				new JSONArray());
		way.osmNodes = Arrays.asList(10L, 12L, 11L);
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"),
				members("way", 20));
		crag.osmNodes = Arrays.asList(10L, 12L, 11L);

		ClimbingRouteCounter.RouteSummary cragSummary = counter.summarize(crag);
		assertEquals(1, cragSummary.getRouteCount());
		assertEquals(Integer.valueOf(1),
				cragSummary.getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(List.of("Dharma"), cragSummary.getRouteNames());
		assertEquals(1, counter.summarize(way).getRouteCount());
	}

	@Test
	public void countsARouteTopOnlyWhenNoOtherRouteHasItsName() throws JSONException {
		addNode(10, route("climbing:sport", "yes", "name", "Été indien"));
		addNode(11, routeTop("climbing:sport", "yes", "name", " ete INDIEN "));
		addNode(12, routeTop("climbing:sport", "yes", "name", "Apoplexie"));
		addNode(13, routeTop("climbing:sport", "yes"));
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"),
				members("node", 10, "node", 11, "node", 12, "node", 13));
		crag.osmNodes = Arrays.asList(10L, 11L, 12L, 13L);

		ClimbingRouteCounter.RouteSummary summary = counter.summarize(crag);
		assertEquals(2, summary.getRouteCount());
		assertEquals(Integer.valueOf(2),
				summary.getStyleCounts().get(GeoNode.ClimbingStyle.sport));
		assertEquals(new HashSet<>(Arrays.asList("Été indien", "Apoplexie")),
				new HashSet<>(summary.getRouteNames()));
		// On its own, a top is the route it marks.
		assertEquals(1, counter.summarize(nodes.get(13L)).getRouteCount());
	}

	@Test
	public void numbersCragRoutesInMemberOrderSkippingTops() throws JSONException {
		addNode(10, route("name", "Dharma"));
		addNode(11, routeTop("name", "Dharma"));
		addNode(12, new JSONObject().put("tourism", "viewpoint"));
		addNode(13, route("name", "Apoplexie"));
		addCollection(20, "way", route("name", "Fissure").put("climbing", "route"),
				new JSONArray());
		OsmCollectionEntity crag = addCollection(2, "relation", group("crag"),
				members("node", 10, "node", 11, "node", 12, "way", 20, "node", 99, "node", 13));

		Map<Long, Integer> expected = new HashMap<>();
		expected.put(10L, 1);
		expected.put(13L, 3);
		assertEquals(expected, counter.numberRoutes(crag));

		OsmCollectionEntity area = addCollection(1, "relation", group("area"),
				members("node", 10, "node", 13));
		assertTrue(counter.numberRoutes(area).isEmpty());
	}

	private Map<String, OsmCollectionEntity> selectCollections(List<Long> ids) {
		Map<String, OsmCollectionEntity> result = new HashMap<>();
		for (OsmCollectionEntity collection : collections.values()) {
			if (ids.contains(collection.osmID)) {
				result.put(DataManager.collectionKey(collection.osmType, collection.osmID),
						collection);
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
		collections.put(DataManager.collectionKey(collection.osmType, collection.osmID),
				collection);
		return collection;
	}
}
