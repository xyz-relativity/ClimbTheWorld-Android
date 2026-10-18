package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONObject;
import org.junit.Test;

public class DataManagerTest {
	@Test
	public void onlyNamedNodesAreDisplayablePois() throws Exception {
		OsmNode namedGym = new OsmNode(new JSONObject()
				.put("id", 3L)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("name", "Climbing gym")));
		OsmNode unnamedGym = new OsmNode(new JSONObject()
				.put("id", 4L)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("leisure", "sports_centre")));
		OsmNode whitespaceOnlyName = new OsmNode(new JSONObject()
				.put("id", 5L)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("name", "  ")));

		assertTrue(DataManager.isDisplayablePoi(namedGym));
		assertFalse(DataManager.isDisplayablePoi(unnamedGym));
		assertFalse(DataManager.isDisplayablePoi(whitespaceOnlyName));
	}

	@Test
	public void namedClimbingFeaturesHavePins() throws Exception {
		assertTrue(DataManager.hasPin(node(6L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom")
				.put("name", "Sport 400"))));
		assertFalse("Unnamed", DataManager.hasPin(node(7L, new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "route_bottom"))));
		assertFalse("Only sport=climbing", DataManager.hasPin(node(8L, new JSONObject()
				.put("sport", "climbing")
				.put("name", "Somewhere"))));
	}

	@Test
	public void buildingGymWayUsesOverpassGeometryCenterForItsMarker() throws Exception {
		OsmCollectionEntity gym = new OsmCollectionEntity(new JSONObject()
				.put("id", 99L)
				.put("type", "way")
				.put("center", new JSONObject().put("lat", 45.25).put("lon", 24.75))
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("building", "yes")));

		DisplayableGeoNode marker = DataManager.toDisplayableNode(gym);

		assertEquals(45.25, marker.geoNode.decimalLatitude, 0);
		assertEquals(24.75, marker.geoNode.decimalLongitude, 0);
	}

	private static OsmNode node(long id, JSONObject tags) throws Exception {
		return new OsmNode(new JSONObject()
				.put("id", id)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", tags));
	}
}
