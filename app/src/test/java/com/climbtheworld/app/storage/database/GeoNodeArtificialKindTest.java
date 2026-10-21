package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Each kind of artificial climbing gets its own pin, so the kind has to follow the tags the
 * OSM wiki gives them and the climbing styles they list.
 */
@RunWith(RobolectricTestRunner.class)
public class GeoNodeArtificialKindTest {
	@Test
	public void sportsCentresAndHallsAreGyms() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.gym,
				kindOf("{\"sport\":\"climbing\",\"leisure\":\"sports_centre\"}"));
		assertEquals(GeoNode.ArtificialKind.gym,
				kindOf("{\"sport\":\"climbing\",\"leisure\":\"sports_hall\"}"));
	}

	@Test
	public void climbingBuildingsAreGyms() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.gym,
				kindOf("way", "{\"sport\":\"climbing\",\"building\":\"yes\"}"));
	}

	@Test
	public void pitchesAreWalls() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.wall,
				kindOf("{\"sport\":\"climbing\",\"leisure\":\"pitch\"}"));
	}

	@Test
	public void climbingTowersAreTowers() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.tower, kindOf("{\"sport\":\"climbing\","
				+ "\"leisure\":\"pitch\",\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"));
		assertEquals(GeoNode.ArtificialKind.tower,
				kindOf("{\"sport\":\"climbing\",\"tower:type\":\"climbing\"}"));
	}

	@Test
	public void gymsWithOnlyBoulderingAreBoulderingGyms() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.boulderGym, kindOf(
				"{\"sport\":\"climbing\",\"leisure\":\"sports_centre\",\"climbing:boulder\":\"yes\"}"));
		// A route count says yes as well.
		assertEquals(GeoNode.ArtificialKind.boulderGym, kindOf(
				"{\"sport\":\"climbing\",\"leisure\":\"sports_hall\",\"climbing:boulder\":\"120\"}"));
	}

	@Test
	public void wallsWithOnlyBoulderingAreBoulderingBlocks() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.boulderBlock, kindOf(
				"{\"sport\":\"climbing\",\"leisure\":\"pitch\",\"climbing:boulder\":\"yes\"}"));
	}

	@Test
	public void boulderingWithRopesKeepsTheGymAndTheWall() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.gym, kindOf("{\"sport\":\"climbing\","
				+ "\"leisure\":\"sports_centre\",\"climbing:boulder\":\"yes\",\"climbing:toprope\":\"yes\"}"));
		assertEquals(GeoNode.ArtificialKind.wall, kindOf("{\"sport\":\"climbing\","
				+ "\"leisure\":\"pitch\",\"climbing:boulder\":\"yes\",\"climbing:sport\":\"yes\"}"));
	}

	@Test
	public void stylesTaggedNoDoNotCount() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.boulderGym, kindOf("{\"sport\":\"climbing\","
				+ "\"leisure\":\"sports_centre\",\"climbing:boulder\":\"yes\",\"climbing:sport\":\"no\"}"));
		assertEquals(GeoNode.ArtificialKind.gym, kindOf("{\"sport\":\"climbing\","
				+ "\"leisure\":\"sports_centre\",\"climbing:boulder\":\"no\"}"));
	}

	@Test
	public void aBoulderingOnlyTowerStaysATower() throws JSONException {
		assertEquals(GeoNode.ArtificialKind.tower, kindOf("{\"sport\":\"climbing\","
				+ "\"leisure\":\"pitch\",\"tower:type\":\"climbing\",\"climbing:boulder\":\"yes\"}"));
	}

	@Test
	public void onlyGymsAreIndoors() {
		for (GeoNode.ArtificialKind kind : GeoNode.ArtificialKind.values()) {
			boolean gym = kind == GeoNode.ArtificialKind.gym
					|| kind == GeoNode.ArtificialKind.boulderGym;
			if (gym) {
				assertTrue(kind.name(), kind.isIndoor());
			} else {
				assertFalse(kind.name(), kind.isIndoor());
			}
		}
	}

	private static GeoNode.ArtificialKind kindOf(String tags) throws JSONException {
		return kindOf("node", tags);
	}

	private static GeoNode.ArtificialKind kindOf(String elementType, String tags)
			throws JSONException {
		GeoNode node = new GeoNode(new JSONObject("{\"type\":\"" + elementType
				+ "\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,\"tags\":" + tags + "}"));
		assertEquals(OsmEntity.EntityClimbingType.artificial, node.getNodeType());
		return node.getArtificialKind();
	}
}
