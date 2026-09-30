package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONObject;
import org.junit.Test;

public class DataManagerNewTest {
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

		assertTrue(DataManagerNew.isDisplayablePoi(namedGym));
		assertFalse(DataManagerNew.isDisplayablePoi(unnamedGym));
		assertFalse(DataManagerNew.isDisplayablePoi(whitespaceOnlyName));
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

		DisplayableGeoNode marker = DataManagerNew.toDisplayableNode(gym);

		assertEquals(45.25, marker.geoNode.decimalLatitude, 0);
		assertEquals(24.75, marker.geoNode.decimalLongitude, 0);
	}
}
