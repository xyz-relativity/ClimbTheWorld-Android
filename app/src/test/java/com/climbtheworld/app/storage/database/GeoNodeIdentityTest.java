package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

/**
 * The A.R. view keeps its pins by POI, and looks one up for every POI around on every frame.
 * POIs are the same when their IDs are, whatever else they hold and whatever they stand for,
 * so the hash has to go by the ID alone, and it is plain arithmetic, with nothing to collect.
 */
@RunWith(RobolectricTestRunner.class)
public class GeoNodeIdentityTest {
	// Node IDs are past 2^32 by now, and the elements not uploaded yet have negative ones.
	private static final long[] IDS = {0, 1, -1, 42, -42, 9974699415L, 14288355603L,
			-9974699415L, 1L << 32, (1L << 32) + 5, -(1L << 32), Integer.MAX_VALUE,
			Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE};
	// What new HashCodeBuilder(17, 31).append(id).toHashCode() gives for each of them, which
	// is how the hash was worked out before.
	private static final int[] BUILDER_HASHES = {527, 528, 527, 569, 568, 1384765348,
			1403454239, 1384765347, 528, 531, 526, -2147483122, -2147483122, -2147483121,
			-2147483121};

	@Test
	public void theHashIsTheOneTheBuilderGave() throws JSONException {
		for (int index = 0; index < IDS.length; index++) {
			assertEquals("ID " + IDS[index], BUILDER_HASHES[index], node(IDS[index]).hashCode());
		}
	}

	@Test
	public void theHashGoesByTheIdAlone() throws JSONException {
		for (long id : IDS) {
			GeoNode node = node(id);
			int hash = node.hashCode();

			node.distanceMeters = 123;
			node.difDegAngle = 45;
			node.updatePOILocation(46.2, -73.7, 300);
			node.getTags().put("name", "Granny Z");

			assertEquals("ID " + id, hash, node.hashCode());
			// The high half of the ID counts as much as the low one.
			assertEquals("ID " + id, 17 * 31 + Long.hashCode(id), hash);
		}
	}

	@Test
	public void poisOfTheSameIdAreTheSame() throws JSONException {
		for (long id : IDS) {
			GeoNode node = node(id);
			GeoNode again = node(id);
			again.distanceMeters = 123;
			again.getTags().put("name", "Granny Z");
			// As the top of a route mapped as a way, which stands for the route at its node.
			GeoNode standIn = new GeoNode(json(id)) {
			};

			assertEquals(node, again);
			assertEquals(again, node);
			assertEquals(node.hashCode(), again.hashCode());
			assertEquals(node, standIn);
			assertEquals(standIn, node);
			assertEquals(node.hashCode(), standIn.hashCode());
		}
	}

	@Test
	public void poisOfOtherIdsAreNot() throws JSONException {
		GeoNode node = node(9974699415L);

		assertNotEquals(node, node(9974699416L));
		// The same low half.
		assertNotEquals(node, node(9974699415L - (1L << 32)));
		assertNotEquals(node, node(-9974699415L));
		assertNotEquals(node, null);
		assertNotEquals(node, Long.valueOf(9974699415L));
	}

	@Test
	public void aPinIsFoundByAnyPoiOfItsNode() throws JSONException {
		Map<GeoNode, String> pins = new HashMap<>();
		for (long id : IDS) {
			pins.put(node(id), "pin " + id);
		}

		assertEquals(IDS.length, pins.size());
		for (long id : IDS) {
			assertEquals("pin " + id, pins.get(node(id)));
			assertEquals("pin " + id, pins.get(new GeoNode(json(id)) {
			}));
		}
	}

	private static GeoNode node(long id) throws JSONException {
		return new GeoNode(json(id));
	}

	private static JSONObject json(long id) throws JSONException {
		return new JSONObject().put("type", "node").put("id", id).put("lat", 45.1)
				.put("lon", -73.2).put("tags", new JSONObject().put("sport", "climbing")
						.put("climbing", "route_bottom"));
	}
}
