package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;

import org.json.JSONObject;
import org.junit.Test;

public class DataManagerNewTest {
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
