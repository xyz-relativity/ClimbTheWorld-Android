package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataManagerNewTest {
	@Test
	public void osmRouteNodeAdaptsToDisplayablePoi() throws Exception {
		OsmNode node = new OsmNode(new JSONObject()
				.put("id", 123L)
				.put("type", "node")
				.put("lat", 45.5)
				.put("lon", 24.5)
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route_bottom")
						.put("name", "North Arete")));
		node.countryIso = "CA";

		DisplayableGeoNode displayable = DataManagerNew.toDisplayableNode(node);

		assertEquals(123L, displayable.geoNode.osmID);
		assertEquals(45.5, displayable.geoNode.decimalLatitude, 0);
		assertEquals(24.5, displayable.geoNode.decimalLongitude, 0);
		assertEquals("North Arete", displayable.geoNode.getName());
		assertEquals(GeoNode.NodeTypes.route, displayable.geoNode.getNodeType());
		assertEquals("CA", displayable.geoNode.countryIso);
	}
}
