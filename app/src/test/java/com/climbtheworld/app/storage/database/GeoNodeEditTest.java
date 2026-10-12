package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.edit.OsmContent;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GeoNodeEditTest {
	@Test
	public void readingANodeLeavesItsTagsAlone() throws JSONException {
		JSONObject json = node("{\"sport\":\"climbing;hiking\",\"climbing\":\"route\",\"ele\":\"1234\"}");
		JSONObject before = new JSONObject(json.toString());

		GeoNode node = new GeoNode(json);

		assertTrue(OsmContent.sameContent(before, node.jsonNodeInfo));
		assertEquals(GeoNode.NodeTypes.route, node.getNodeType());
		assertEquals(1234, node.elevationMeters, 0);
	}

	@Test
	public void climbingAmongOtherSportsIsRecognised() throws JSONException {
		assertEquals(GeoNode.NodeTypes.crag, GeoNode.NodeTypes.getNodeTypeFromJson(
				new JSONObject("{\"sport\":\"hiking;climbing\",\"climbing\":\"crag\"}")));
		assertEquals(OsmEntity.EntityClimbingType.route, OsmEntity.EntityClimbingType.getNodeTypeFromJson(
				new JSONObject("{\"sport\":\"climbing; hiking\",\"climbing\":\"route_bottom\"}")));
		assertEquals(GeoNode.NodeTypes.unknown, GeoNode.NodeTypes.getNodeTypeFromJson(
				new JSONObject("{\"sport\":\"ice_climbing\",\"climbing\":\"crag\"}")));
	}

	@Test
	public void keepingTheTypeChangesNoTag() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing\",\"climbing\":\"route\"}"));

		node.setClimbingType(GeoNode.NodeTypes.route);

		assertEquals("route", node.getKey(ClimbingTags.KEY_CLIMBING));
	}

	@Test
	public void changingTheTypeKeepsOtherSportsAndLeisure() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing;hiking\",\"climbing\":\"route_bottom\","
				+ "\"leisure\":\"nature_reserve\"}"));

		node.setClimbingType(GeoNode.NodeTypes.crag);

		assertEquals("climbing;hiking", node.getKey(ClimbingTags.KEY_SPORT));
		assertEquals("crag", node.getKey(ClimbingTags.KEY_CLIMBING));
		assertEquals("nature_reserve", node.getKey(ClimbingTags.KEY_LEISURE));
	}

	@Test
	public void gymTypeComesAndGoesWithTheSportsCentre() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"hiking\",\"climbing\":\"route_bottom\"}"));

		node.setClimbingType(GeoNode.NodeTypes.artificial);
		assertEquals("climbing", node.getKey(ClimbingTags.KEY_SPORT));
		assertEquals("sports_centre", node.getKey(ClimbingTags.KEY_LEISURE));
		assertFalse(node.getTags().has(ClimbingTags.KEY_CLIMBING));

		node.setClimbingType(GeoNode.NodeTypes.route);
		assertFalse(node.getTags().has(ClimbingTags.KEY_LEISURE));
		assertEquals("route_bottom", node.getKey(ClimbingTags.KEY_CLIMBING));
	}

	@Test
	public void websiteStaysUnderTheKeyInUse() throws JSONException {
		GeoNode plain = new GeoNode(node("{\"website\":\"https://a.example\"}"));
		plain.setWebsite("https://b.example");
		assertEquals("https://b.example", plain.getKey(ClimbingTags.KEY_WEBSITE));
		assertFalse(plain.getTags().has(ClimbingTags.KEY_CONTACT_WEBSITE));

		GeoNode none = new GeoNode(node("{}"));
		none.setPhone("+1 555");
		assertEquals("+1 555", none.getKey(ClimbingTags.KEY_CONTACT_PHONE));

		GeoNode both = new GeoNode(node("{\"website\":\"a\",\"contact:website\":\"b\"}"));
		both.setWebsite("");
		assertFalse(both.getTags().has(ClimbingTags.KEY_WEBSITE));
		assertFalse(both.getTags().has(ClimbingTags.KEY_CONTACT_WEBSITE));
	}

	@Test
	public void elevationIsWrittenAsOsmText() throws JSONException {
		GeoNode node = new GeoNode(node("{}"));

		node.updatePOILocation(45, -73, 1234.0);
		assertEquals("1234", node.getTags().get(ClimbingTags.KEY_ELEVATION));

		node.updatePOILocation(45, -73, 1234.5);
		assertEquals("1234.5", node.getTags().get(ClimbingTags.KEY_ELEVATION));
	}

	@Test
	public void unknownGradeRemovesTheGrade() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:french\":\"6a\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf(ClimbingTags.UNKNOWN_GRADE_STRING),
				ClimbingTags.KEY_GRADE_TAG);

		assertEquals(0, node.getTags().length());
	}

	private static JSONObject node(String tags) throws JSONException {
		return new JSONObject("{\"type\":\"node\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,\"tags\":" + tags + "}");
	}
}
