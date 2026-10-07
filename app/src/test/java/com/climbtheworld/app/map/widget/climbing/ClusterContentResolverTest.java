package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ClusterContentResolverTest {
	private final List<OsmCollectionEntity> relations = new ArrayList<>();
	private final List<MapBounds> queriedBounds = new ArrayList<>();
	private final ClusterContentResolver resolver = new ClusterContentResolver(bounds -> {
		queriedBounds.add(bounds);
		return relations;
	});

	@Test
	public void poisAreShownThroughTheirOutermostRelationOnce() throws JSONException {
		OsmCollectionEntity area = addRelation(1, group("area"), members("relation", 2,
				"relation", 3));
		addRelation(2, group("crag"), members("node", 10, "node", 11));
		addRelation(3, group("crag"), members("node", 12));
		OsmCollectionEntity lonelyCrag = addRelation(4, group("crag"), members("node", 13));

		ClusterContentResolver.Content content = resolver.resolve(Arrays.asList(
				poi("node", 10), poi("node", 11), poi("node", 12), poi("node", 13)));

		assertEquals(Arrays.asList(area, lonelyCrag), content.relations);
		assertTrue(content.pois.isEmpty());
	}

	@Test
	public void poisInNoRelationAreListedThemselves() throws JSONException {
		OsmCollectionEntity crag = addRelation(2, group("crag"), members("node", 10));
		DisplayableGeoNode gym = poi("node", 20);
		// A way sharing the id of a member node is a different element.
		DisplayableGeoNode buildingGym = poi("way", 10);

		ClusterContentResolver.Content content = resolver.resolve(Arrays.asList(
				poi("node", 10), gym, buildingGym));

		assertEquals(Collections.singletonList(crag), content.relations);
		assertEquals(Arrays.asList(gym, buildingGym), content.pois);
	}

	@Test
	public void wayPoisAreFoundThroughTheirWayMembership() throws JSONException {
		OsmCollectionEntity area = addRelation(1, group("area"), members("way", 30));

		ClusterContentResolver.Content content = resolver.resolve(
				Collections.singletonList(poi("way", 30)));

		assertEquals(Collections.singletonList(area), content.relations);
		assertTrue(content.pois.isEmpty());
	}

	@Test
	public void poiInTwoSeparateRelationsListsBoth() throws JSONException {
		OsmCollectionEntity first = addRelation(1, group("crag"), members("node", 10));
		OsmCollectionEntity second = addRelation(2, group("crag"), members("node", 10));

		ClusterContentResolver.Content content = resolver.resolve(
				Collections.singletonList(poi("node", 10)));

		assertEquals(Arrays.asList(first, second), content.relations);
	}

	@Test
	public void relationsNestingEachOtherFallBackToTheDirectRelation() throws JSONException {
		OsmCollectionEntity crag = addRelation(2, group("crag"), members("node", 10,
				"relation", 1));
		addRelation(1, group("area"), members("relation", 2));

		ClusterContentResolver.Content content = resolver.resolve(
				Collections.singletonList(poi("node", 10)));

		assertEquals(Collections.singletonList(crag), content.relations);
		assertTrue(content.pois.isEmpty());
	}

	@Test
	public void waysFromTheSourceAreNotRelations() throws JSONException {
		relations.add(new OsmCollectionEntity(new JSONObject()
				.put("id", 5L)
				.put("type", "way")
				.put("tags", group("crag"))
				.put("nodes", new JSONArray().put(10L))));

		ClusterContentResolver.Content content = resolver.resolve(
				Collections.singletonList(poi("node", 10)));

		assertTrue(content.relations.isEmpty());
		assertEquals(1, content.pois.size());
	}

	@Test
	public void relationsAreQueriedAroundThePois() throws JSONException {
		resolver.resolve(Arrays.asList(poi("node", 10, 45, 24), poi("node", 11, 46, 25)));

		MapBounds bounds = queriedBounds.get(0);
		assertTrue(bounds.contains(new MapCoordinate(45, 24)));
		assertTrue(bounds.contains(new MapCoordinate(46, 25)));
		// Padded, so a relation whose box only touches a POI still intersects.
		assertTrue(bounds.getSouth() < 45 && bounds.getWest() < 24);
		assertTrue(bounds.getNorth() > 46 && bounds.getEast() > 25);
	}

	@Test
	public void noPoisQueryNothing() {
		ClusterContentResolver.Content content = resolver.resolve(Collections.emptyList());

		assertTrue(content.relations.isEmpty());
		assertTrue(content.pois.isEmpty());
		assertTrue(queriedBounds.isEmpty());
	}

	private OsmCollectionEntity addRelation(long id, JSONObject tags, JSONArray members)
			throws JSONException {
		OsmCollectionEntity relation = new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "relation")
				.put("tags", tags)
				.put("members", members));
		relations.add(relation);
		return relation;
	}

	private static DisplayableGeoNode poi(String type, long id) throws JSONException {
		return poi(type, id, 45, 24);
	}

	private static DisplayableGeoNode poi(String type, long id, double latitude,
	                                      double longitude) throws JSONException {
		return new DisplayableGeoNode(new GeoNode(new JSONObject()
				.put("id", id)
				.put("type", type)
				.put("lat", latitude)
				.put("lon", longitude)
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route_bottom")
						.put("name", type + id))));
	}

	private static JSONObject group(String climbing) throws JSONException {
		return new JSONObject()
				.put("type", "site")
				.put("site", "climbing")
				.put("sport", "climbing")
				.put("climbing", climbing);
	}

	private static JSONArray members(Object... typeAndRefs) throws JSONException {
		JSONArray result = new JSONArray();
		for (int index = 0; index < typeAndRefs.length; index += 2) {
			result.put(new JSONObject()
					.put("type", typeAndRefs[index])
					.put("ref", ((Number) typeAndRefs[index + 1]).longValue())
					.put("role", ""));
		}
		return result;
	}
}
