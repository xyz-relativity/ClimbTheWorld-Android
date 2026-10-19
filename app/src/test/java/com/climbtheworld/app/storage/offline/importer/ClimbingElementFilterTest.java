package com.climbtheworld.app.storage.offline.importer;

import static org.junit.Assert.assertEquals;

import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@RunWith(RobolectricTestRunner.class)
public class ClimbingElementFilterTest {
	private static final String ROUTE_BOTTOM = "{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\"R\"}";

	@Test
	public void routeWaysKeepTheirNodesAndCliffsGoWithTheirs() throws JSONException {
		assertKept(retain(
						node(1, ROUTE_BOTTOM),
						node(2, "{}"),
						node(3, "{\"climbing\":\"route_top\"}"),
						way(10, "{\"sport\":\"climbing\",\"climbing\":\"route\"}", 1, 2, 3),
						// Shares node 2 with the route.
						way(40, "{\"sport\":\"climbing\",\"natural\":\"cliff\"}", 7, 2, 8),
						node(7, "{}"),
						node(8, "{}")),
				"node|1", "node|2", "node|3", "way|10");
	}

	@Test
	public void relationsKeepTheirMembersAtAnyDepth() throws JSONException {
		assertKept(retain(
						relation(1, "{\"type\":\"site\",\"climbing\":\"area\"}", "relation", 2),
						relation(2, "{\"type\":\"site\",\"climbing\":\"crag\"}", "node", 4, "way", 10),
						node(4, ROUTE_BOTTOM),
						way(10, "{\"climbing\":\"route\"}", 11, 12),
						node(11, "{}"),
						node(12, "{}"),
						// A gym's outline is an untagged way.
						relation(30, "{\"type\":\"multipolygon\",\"sport\":\"climbing\","
								+ "\"leisure\":\"sports_centre\"}", "way", 31),
						way(31, "{}", 5, 6, 5),
						node(5, "{}"),
						node(6, "{}")),
				"node|11", "node|12", "node|4", "node|5", "node|6", "relation|1", "relation|2",
				"relation|30", "way|10", "way|31");
	}

	@Test
	public void relationsThatAreNoClimbingFeatureGoWithTheirMembers() throws JSONException {
		assertKept(retain(
						relation(50, "{\"type\":\"multipolygon\",\"climbing\":\"crag\"}",
								"node", 4, "node", 9, "way", 20),
						relation(51, "{\"type\":\"route\",\"route\":\"hiking\",\"sport\":\"climbing\"}",
								"way", 20),
						node(4, ROUTE_BOTTOM),
						node(9, "{}"),
						way(20, "{\"highway\":\"path\"}", 9)),
				"node|4");
	}

	@Test
	public void climbingTagsThatAreNoFeatureAreDropped() throws JSONException {
		assertKept(retain(
						node(1, "{\"sport\":\"climbing\",\"climbing\":\"yes\",\"name\":\"x\"}"),
						node(2, "{\"sport\":\"climbing\",\"natural\":\"peak\",\"name\":\"x\"}"),
						node(3, "{\"sport\":\"climbing\",\"name\":\"x\"}"),
						way(4, "{\"sport\":\"climbing\",\"climbing\":\"crag\"}"),
						way(5, "{\"sport\":\"climbing\",\"leisure\":\"pitch\"}")),
				"way|5");
	}

	@Test
	public void relationsInACycleAndMissingMembersAreHandled() throws JSONException {
		assertKept(retain(
						relation(1, "{\"type\":\"site\",\"climbing\":\"area\"}", "relation", 2,
								"node", 99, "way", 98),
						relation(2, "{\"type\":\"site\",\"climbing\":\"area\"}", "relation", 1)),
				"relation|1", "relation|2");
	}

	private static Set<String> retain(Object... elements) {
		Map<Long, OsmNode> nodes = new HashMap<>();
		Map<String, OsmCollectionEntity> collections = new HashMap<>();
		for (Object element : elements) {
			if (element instanceof OsmNode) {
				nodes.put(((OsmNode) element).osmID, (OsmNode) element);
			} else {
				OsmCollectionEntity collection = (OsmCollectionEntity) element;
				collections.put(collection.osmType + "|" + collection.osmID, collection);
			}
		}

		ClimbingElementFilter.retain(nodes.values(), collections.values());

		Set<String> kept = new TreeSet<>(collections.keySet());
		for (Long id : nodes.keySet()) {
			kept.add("node|" + id);
		}
		return kept;
	}

	private static void assertKept(Set<String> kept, String... expected) {
		assertEquals(new TreeSet<>(Arrays.asList(expected)), kept);
	}

	private static OsmNode node(long id, String tags) throws JSONException {
		return new OsmNode(new JSONObject("{\"type\":\"node\",\"id\":" + id
				+ ",\"lat\":45.0,\"lon\":-75.0,\"tags\":" + tags + "}"));
	}

	private static OsmCollectionEntity way(long id, String tags, long... nodes) throws JSONException {
		StringBuilder ids = new StringBuilder();
		for (long node : nodes) {
			ids.append(ids.length() > 0 ? "," : "").append(node);
		}
		return new OsmCollectionEntity(new JSONObject("{\"type\":\"way\",\"id\":" + id
				+ ",\"nodes\":[" + ids + "],\"tags\":" + tags + "}"));
	}

	/** @param members each member's type followed by its id */
	private static OsmCollectionEntity relation(long id, String tags, Object... members)
			throws JSONException {
		StringBuilder list = new StringBuilder();
		for (int index = 0; index + 1 < members.length; index += 2) {
			list.append(list.length() > 0 ? "," : "").append("{\"type\":\"").append(members[index])
					.append("\",\"ref\":").append(members[index + 1]).append(",\"role\":\"\"}");
		}
		return new OsmCollectionEntity(new JSONObject("{\"type\":\"relation\",\"id\":" + id
				+ ",\"members\":[" + list + "],\"tags\":" + tags + "}"));
	}
}
