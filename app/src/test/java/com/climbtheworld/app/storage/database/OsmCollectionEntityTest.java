package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class OsmCollectionEntityTest {
	private static final String CENTER = "\"center\":{\"lat\":45.5,\"lon\":-73.6}";

	@Test
	public void aWayWithoutItsNodesIsPlacedAtItsCenter() throws JSONException {
		OsmCollectionEntity gym = new OsmCollectionEntity(new JSONObject("{\"type\":\"way\",\"id\":1,"
				+ "\"nodes\":[1,2,3,1]," + CENTER + ",\"tags\":{\"sport\":\"climbing\","
				+ "\"leisure\":\"sports_centre\"}}"));

		gym.computeCache(Collections.emptyList());

		assertEquals(45.5, gym.bBoxNorth, 0);
		assertEquals(45.5, gym.bBoxSouth, 0);
		assertEquals(-73.6, gym.bBoxEast, 0);
		assertEquals(-73.6, gym.bBoxWest, 0);
	}

	@Test
	public void theNodesOfAWayWinOverItsCenter() throws JSONException {
		OsmCollectionEntity route = new OsmCollectionEntity(new JSONObject("{\"type\":\"way\",\"id\":1,"
				+ "\"nodes\":[1]," + CENTER + ",\"tags\":{\"climbing\":\"route\"}}"));
		OsmNode bottom = new OsmNode(new JSONObject("{\"type\":\"node\",\"id\":1,\"lat\":46.0,"
				+ "\"lon\":-74.0,\"tags\":{}}"));

		route.computeCache(Collections.singletonList(bottom));

		assertEquals(46.0, route.bBoxNorth, 0);
		assertEquals(-74.0, route.bBoxEast, 0);
	}

	@Test
	public void aRelationWithoutItsMembersHasNoLocation() throws JSONException {
		OsmCollectionEntity crag = new OsmCollectionEntity(new JSONObject("{\"type\":\"relation\","
				+ "\"id\":1,\"members\":[]," + CENTER + ",\"tags\":{\"type\":\"site\","
				+ "\"climbing\":\"crag\"}}"));

		crag.computeCache(Collections.emptyList());

		assertEquals(0, crag.bBoxNorth, 0);
		assertEquals(0, crag.bBoxEast, 0);
	}
}
