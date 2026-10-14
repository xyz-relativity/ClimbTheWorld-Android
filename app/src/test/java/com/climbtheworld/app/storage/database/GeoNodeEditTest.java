package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertArrayEquals;
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
	public void nodeEditorOffersCragAndAreaOnlyToNodesThatAreOne() {
		GeoNode.NodeTypes route = GeoNode.NodeTypes.route;
		GeoNode.NodeTypes artificial = GeoNode.NodeTypes.artificial;

		assertArrayEquals(new GeoNode.NodeTypes[]{route, artificial},
				GeoNode.NodeTypes.nodeEditorValues(route));
		assertArrayEquals(new GeoNode.NodeTypes[]{route, artificial},
				GeoNode.NodeTypes.nodeEditorValues(artificial));
		assertArrayEquals(new GeoNode.NodeTypes[]{route, GeoNode.NodeTypes.crag, artificial},
				GeoNode.NodeTypes.nodeEditorValues(GeoNode.NodeTypes.crag));
		assertArrayEquals(new GeoNode.NodeTypes[]{route, GeoNode.NodeTypes.area, artificial},
				GeoNode.NodeTypes.nodeEditorValues(GeoNode.NodeTypes.area));
		assertArrayEquals(new GeoNode.NodeTypes[]{route, artificial, GeoNode.NodeTypes.unknown},
				GeoNode.NodeTypes.nodeEditorValues(GeoNode.NodeTypes.unknown));
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

	@Test(timeout = 2000)
	public void typeIsFoundQuicklyInLongTags() throws JSONException {
		// A crag with a long description took minutes when the route pattern was tested at every
		// position of the text.
		JSONObject tags = new JSONObject().put("sport", "climbing").put("climbing", "crag")
				.put("description", "x ".repeat(5000));

		assertEquals(OsmEntity.EntityClimbingType.crag,
				OsmEntity.EntityClimbingType.getNodeTypeFromJson(tags));
		assertEquals(GeoNode.NodeTypes.crag, GeoNode.NodeTypes.getNodeTypeFromJson(tags));
	}

	@Test
	public void gradedBouldersAreRoutes() throws JSONException {
		String graded = "{\"sport\":\"climbing\",\"climbing\":\"boulder\",\"natural\":\"stone\","
				+ "\"climbing:grade:french\":\"6a\",\"ref\":\"12\"}";
		String rock = "{\"sport\":\"climbing\",\"climbing\":\"boulder\",\"natural\":\"stone\"}";

		assertEquals(GeoNode.NodeTypes.route, GeoNode.NodeTypes.getNodeTypeFromJson(new JSONObject(graded)));
		assertEquals(OsmEntity.EntityClimbingType.route,
				OsmEntity.EntityClimbingType.getNodeTypeFromJson(new JSONObject(graded)));
		assertEquals(GeoNode.NodeTypes.unknown, GeoNode.NodeTypes.getNodeTypeFromJson(new JSONObject(rock)));
		assertEquals(OsmEntity.EntityClimbingType.others,
				OsmEntity.EntityClimbingType.getNodeTypeFromJson(new JSONObject(rock)));
	}

	@Test
	public void editingABoulderKeepsItsTag() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing\",\"climbing\":\"boulder\","
				+ "\"climbing:grade:french\":\"6a\"}"));

		node.setClimbingType(GeoNode.NodeTypes.route);
		node.setLevelFromID(GradeSystem.uiaa.indexOf("7"), ClimbingTags.KEY_GRADE_TAG);

		assertEquals("boulder", node.getKey(ClimbingTags.KEY_CLIMBING));
		assertFalse(node.isRouteTop());
	}

	@Test
	public void wallsAndTowersTaggedAsTheWikiHasThemAreArtificial() throws JSONException {
		String[] facilities = {
				"{\"sport\":\"climbing\",\"leisure\":\"pitch\"}",
				"{\"sport\":\"climbing\",\"leisure\":\"sports_hall\"}",
				"{\"sport\":\"climbing\",\"leisure\":\"pitch\",\"man_made\":\"tower\",\"tower:type\":\"climbing\"}",
				"{\"sport\":\"climbing\",\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"};
		for (String tags : facilities) {
			assertEquals(tags, GeoNode.NodeTypes.artificial,
					GeoNode.NodeTypes.getNodeTypeFromJson(new JSONObject(tags)));
			assertEquals(tags, OsmEntity.EntityClimbingType.artificial,
					OsmEntity.EntityClimbingType.getNodeTypeFromJson(new JSONObject(tags)));
		}
		assertEquals(GeoNode.NodeTypes.unknown, GeoNode.NodeTypes.getNodeTypeFromJson(
				new JSONObject("{\"sport\":\"soccer\",\"leisure\":\"pitch\"}")));
	}

	@Test
	public void wallTypeGoesWithItsPitch() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing\",\"leisure\":\"pitch\","
				+ "\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"));

		node.setClimbingType(GeoNode.NodeTypes.route);

		assertFalse(node.getTags().has(ClimbingTags.KEY_LEISURE));
		assertEquals("route_bottom", node.getKey(ClimbingTags.KEY_CLIMBING));
	}

	@Test
	public void aPitchIsAWallUnlessItIsATower() throws JSONException {
		assertTrue(new GeoNode(node("{\"sport\":\"climbing\",\"leisure\":\"pitch\"}")).isClimbingWall());

		GeoNode tower = new GeoNode(node("{\"sport\":\"climbing\",\"leisure\":\"pitch\","
				+ "\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"));
		assertTrue(tower.isArtificialTower());
		assertFalse(tower.isClimbingWall());

		assertFalse(new GeoNode(node("{\"sport\":\"climbing\",\"leisure\":\"sports_centre\"}"))
				.isClimbingWall());
	}

	@Test
	public void websiteStaysUnderTheKeyInUse() throws JSONException {
		GeoNode plain = new GeoNode(node("{\"website\":\"https://a.example\"}"));
		plain.setWebsite("https://b.example");
		assertEquals("https://b.example", plain.getKey(ClimbingTags.KEY_WEBSITE));
		assertFalse(plain.getTags().has(ClimbingTags.KEY_CONTACT_WEBSITE));

		GeoNode contact = new GeoNode(node("{\"contact:website\":\"https://a.example\"}"));
		contact.setWebsite("https://b.example");
		assertEquals("https://b.example", contact.getKey(ClimbingTags.KEY_CONTACT_WEBSITE));
		assertFalse(contact.getTags().has(ClimbingTags.KEY_WEBSITE));

		// A new one goes under the plain key, as the OSM wiki's Climbing page has it.
		GeoNode none = new GeoNode(node("{}"));
		none.setPhone("+1 555");
		none.setWebsite("https://c.example");
		assertEquals("+1 555", none.getKey(ClimbingTags.KEY_PHONE));
		assertEquals("https://c.example", none.getKey(ClimbingTags.KEY_WEBSITE));
		assertFalse(none.getTags().has(ClimbingTags.KEY_CONTACT_PHONE));
		assertFalse(none.getTags().has(ClimbingTags.KEY_CONTACT_WEBSITE));

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
	public void settingAGradeConvertsItIntoTheOtherSystemsAndAddsUiaa() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:french\":\"6a\",\"climbing:grade:aid\":\"A1\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf("7"), ClimbingTags.KEY_GRADE_TAG);

		assertEquals("7", node.getKey("climbing:grade:uiaa"));
		assertEquals("6b", node.getKey("climbing:grade:french"));
		assertEquals("A1", node.getKey("climbing:grade:aid"));
	}

	@Test
	public void settingAGradeWritesFontIntoAnFbTag() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:fb\":\"6A\"}"));

		node.setLevelFromID(GradeSystem.vGrade.indexOf("V7"), ClimbingTags.KEY_GRADE_TAG);

		assertEquals("7A+", node.getKey("climbing:grade:fb"));
	}

	@Test
	public void settingAGradeUpdatesTheUiaaGradeInPlace() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:UIAA\":\"6\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf("7-"), ClimbingTags.KEY_GRADE_TAG);

		assertEquals("7-", node.getKey("climbing:grade:UIAA"));
		assertEquals(1, node.getTags().length());
	}

	@Test
	public void settingAGradeRangeLeavesTheGradeAlone() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:french\":\"6a\",\"climbing:grade:french:min\":\"5a\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf("5+"), ClimbingTags.KEY_GRADE_TAG_MIN);

		assertEquals("5+", node.getKey("climbing:grade:uiaa:min"));
		assertEquals("5b", node.getKey("climbing:grade:french:min"));
		assertEquals("6a", node.getKey("climbing:grade:french"));
		assertFalse(node.getTags().has("climbing:grade:uiaa"));
	}

	@Test
	public void unknownGradeRemovesTheGradesTheAppCanRead() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:french\":\"6a\",\"climbing:grade:aid\":\"A1\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf(ClimbingTags.UNKNOWN_GRADE_STRING),
				ClimbingTags.KEY_GRADE_TAG);

		assertEquals(1, node.getTags().length());
		assertEquals("A1", node.getKey("climbing:grade:aid"));
	}

	private static JSONObject node(String tags) throws JSONException {
		return new JSONObject("{\"type\":\"node\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,\"tags\":" + tags + "}");
	}
}
