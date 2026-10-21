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

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class GeoNodeEditTest {
	@Test
	public void readingANodeLeavesItsTagsAlone() throws JSONException {
		JSONObject json = node("{\"sport\":\"climbing;hiking\",\"climbing\":\"route\",\"ele\":\"1234\"}");
		JSONObject before = new JSONObject(json.toString());

		GeoNode node = new GeoNode(json);

		assertTrue(OsmContent.sameContent(before, node.jsonNodeInfo));
		assertEquals(OsmEntity.EntityClimbingType.route, node.getNodeType());
		assertEquals(1234, node.elevationMeters, 0);
	}

	@Test
	public void climbingAmongOtherSportsMakesAFacility() throws JSONException {
		assertEquals(OsmEntity.EntityClimbingType.artificial, OsmEntity.EntityClimbingType.ofPin(
				node("{\"sport\":\"hiking;climbing\",\"leisure\":\"pitch\"}")));
		assertEquals(OsmEntity.EntityClimbingType.artificial, OsmEntity.EntityClimbingType.fromElement(
				node("{\"sport\":\"climbing; hiking\",\"leisure\":\"sports_centre\"}")));
		assertEquals(OsmEntity.EntityClimbingType.others, OsmEntity.EntityClimbingType.ofPin(
				node("{\"sport\":\"ice_climbing\",\"leisure\":\"pitch\"}")));
	}

	@Test
	public void bothTypesComeFromTheClimbingFeature() throws JSONException {
		JSONObject crag = node("{\"climbing\":\"crag\"}");
		JSONObject cliff = new JSONObject("{\"type\":\"way\",\"id\":1,\"nodes\":[1,2],"
				+ "\"tags\":{\"sport\":\"climbing\",\"natural\":\"cliff\"}}");

		assertEquals(OsmEntity.EntityClimbingType.crag, OsmEntity.EntityClimbingType.ofPin(crag));
		assertEquals(OsmEntity.EntityClimbingType.crag, OsmEntity.EntityClimbingType.fromElement(crag));
		assertEquals(OsmEntity.EntityClimbingType.others, OsmEntity.EntityClimbingType.ofPin(cliff));
		assertEquals(OsmEntity.EntityClimbingType.others,
				OsmEntity.EntityClimbingType.fromElement(cliff));
		assertEquals(OsmEntity.EntityClimbingType.NAN,
				OsmEntity.EntityClimbingType.fromElement(node("{}")));
	}

	@Test
	public void nodeEditorOffersCragAndAreaOnlyToNodesThatAreOne() {
		OsmEntity.EntityClimbingType route = OsmEntity.EntityClimbingType.route;
		OsmEntity.EntityClimbingType artificial = OsmEntity.EntityClimbingType.artificial;

		assertArrayEquals(new OsmEntity.EntityClimbingType[]{route, artificial},
				OsmEntity.EntityClimbingType.nodeEditorValues(route));
		assertArrayEquals(new OsmEntity.EntityClimbingType[]{route, artificial},
				OsmEntity.EntityClimbingType.nodeEditorValues(artificial));
		assertArrayEquals(new OsmEntity.EntityClimbingType[]{route, OsmEntity.EntityClimbingType.crag, artificial},
				OsmEntity.EntityClimbingType.nodeEditorValues(OsmEntity.EntityClimbingType.crag));
		assertArrayEquals(new OsmEntity.EntityClimbingType[]{route, OsmEntity.EntityClimbingType.area, artificial},
				OsmEntity.EntityClimbingType.nodeEditorValues(OsmEntity.EntityClimbingType.area));
		assertArrayEquals(new OsmEntity.EntityClimbingType[]{route, artificial, OsmEntity.EntityClimbingType.others},
				OsmEntity.EntityClimbingType.nodeEditorValues(OsmEntity.EntityClimbingType.others));
	}

	@Test
	public void keepingTheTypeChangesNoTag() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing\",\"climbing\":\"route\"}"));

		node.setClimbingType(OsmEntity.EntityClimbingType.route);

		assertEquals("route", node.getKey(ClimbingTags.KEY_CLIMBING));
	}

	@Test
	public void changingTheTypeKeepsOtherSportsAndLeisure() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing;hiking\",\"climbing\":\"route_bottom\","
				+ "\"leisure\":\"nature_reserve\"}"));

		node.setClimbingType(OsmEntity.EntityClimbingType.crag);

		assertEquals("climbing;hiking", node.getKey(ClimbingTags.KEY_SPORT));
		assertEquals("crag", node.getKey(ClimbingTags.KEY_CLIMBING));
		assertEquals("nature_reserve", node.getKey(ClimbingTags.KEY_LEISURE));
	}

	@Test
	public void gymTypeComesAndGoesWithTheSportsCentre() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"hiking\",\"climbing\":\"route_bottom\"}"));

		node.setClimbingType(OsmEntity.EntityClimbingType.artificial);
		assertEquals("climbing", node.getKey(ClimbingTags.KEY_SPORT));
		assertEquals("sports_centre", node.getKey(ClimbingTags.KEY_LEISURE));
		assertFalse(node.getTags().has(ClimbingTags.KEY_CLIMBING));

		node.setClimbingType(OsmEntity.EntityClimbingType.route);
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
				OsmEntity.EntityClimbingType.fromElement(node(tags.toString())));
		assertEquals(OsmEntity.EntityClimbingType.crag, OsmEntity.EntityClimbingType.ofPin(node(tags.toString())));
	}

	@Test
	public void gradedBouldersAreRoutes() throws JSONException {
		String graded = "{\"sport\":\"climbing\",\"climbing\":\"boulder\",\"natural\":\"stone\","
				+ "\"climbing:grade:french\":\"6a\",\"ref\":\"12\"}";
		String rock = "{\"sport\":\"climbing\",\"climbing\":\"boulder\",\"natural\":\"stone\"}";

		assertEquals(OsmEntity.EntityClimbingType.route, OsmEntity.EntityClimbingType.ofPin(node(graded)));
		assertEquals(OsmEntity.EntityClimbingType.route,
				OsmEntity.EntityClimbingType.fromElement(node(graded)));
		assertEquals(OsmEntity.EntityClimbingType.others, OsmEntity.EntityClimbingType.ofPin(node(rock)));
		assertEquals(OsmEntity.EntityClimbingType.others,
				OsmEntity.EntityClimbingType.fromElement(node(rock)));
	}

	@Test
	public void editingABoulderKeepsItsTag() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing\",\"climbing\":\"boulder\","
				+ "\"climbing:grade:french\":\"6a\"}"));

		node.setClimbingType(OsmEntity.EntityClimbingType.route);
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
			assertEquals(tags, OsmEntity.EntityClimbingType.artificial,
					OsmEntity.EntityClimbingType.ofPin(node(tags)));
			assertEquals(tags, OsmEntity.EntityClimbingType.artificial,
					OsmEntity.EntityClimbingType.fromElement(node(tags)));
		}
		assertEquals(OsmEntity.EntityClimbingType.others, OsmEntity.EntityClimbingType.ofPin(
				node("{\"sport\":\"soccer\",\"leisure\":\"pitch\"}")));
	}

	@Test
	public void wallTypeGoesWithItsPitch() throws JSONException {
		GeoNode node = new GeoNode(node("{\"sport\":\"climbing\",\"leisure\":\"pitch\","
				+ "\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"));

		node.setClimbingType(OsmEntity.EntityClimbingType.route);

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
	public void elevationCanBeRemovedOrSetToSeaLevel() throws JSONException {
		GeoNode node = new GeoNode(node("{\"ele\":\"1234\"}"));

		node.setElevation(null);
		assertFalse(node.getTags().has(ClimbingTags.KEY_ELEVATION));
		assertEquals(0, node.elevationMeters, 0);

		node.setElevation(0.0);
		assertEquals("0", node.getKey(ClimbingTags.KEY_ELEVATION));

		node.setElevation(-12.5);
		assertEquals("-12.5", node.getKey(ClimbingTags.KEY_ELEVATION));
	}

	@Test
	public void elevationIsReadWithItsSign() throws JSONException {
		assertEquals(-5, new GeoNode(node("{\"ele\":\"-5 m\"}")).elevationMeters, 0);
		assertEquals(1234, new GeoNode(node("{\"ele\":\"1,234\"}")).elevationMeters, 0);
		assertEquals(812.5, new GeoNode(node("{\"ele\":\"812.5\"}")).elevationMeters, 0);
		// No number used to throw while reading the node.
		assertEquals(0, new GeoNode(node("{\"ele\":\"unknown\"}")).elevationMeters, 0);
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
		assertEquals("5a", node.getKey("climbing:grade:french:min"));
		assertEquals("6a", node.getKey("climbing:grade:french"));
		assertFalse(node.getTags().has("climbing:grade:uiaa"));
	}

	@Test
	public void keepingTheShownGradeOnlyAddsUiaa() throws JSONException {
		// 6c+ shows as UIAA 8-, which starts on an easier row.
		GeoNode node = new GeoNode(node("{\"climbing:grade:french\":\"6c+\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf("8-"), ClimbingTags.KEY_GRADE_TAG, GradeSystem.uiaa);

		assertEquals("6c+", node.getKey("climbing:grade:french"));
		assertEquals("8-", node.getKey("climbing:grade:uiaa"));
	}

	@Test
	public void keepingTheShownGradeLeavesAUiaaGradeAlone() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:UIAA\":\"8-\",\"climbing:grade:french\":\"6c+\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf("8-"), ClimbingTags.KEY_GRADE_TAG, GradeSystem.uiaa);

		assertEquals("6c+", node.getKey("climbing:grade:french"));
		assertEquals(2, node.getTags().length());
	}

	@Test
	public void pickingAnotherShownGradeConvertsIt() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:french\":\"6c+\"}"));

		node.setLevelFromID(GradeSystem.uiaa.indexOf("8"), ClimbingTags.KEY_GRADE_TAG, GradeSystem.uiaa);

		assertEquals("7a", node.getKey("climbing:grade:french"));
		assertEquals("8", node.getKey("climbing:grade:uiaa"));
	}

	@Test
	public void keepingNoGradeAddsNone() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:aid\":\"A1\"}"));

		node.setLevelFromID(-1, ClimbingTags.KEY_GRADE_TAG, GradeSystem.french);

		assertEquals(1, node.getTags().length());
	}

	@Test
	public void aGradePickedInASystemIsSavedInItAlone() throws JSONException {
		GeoNode node = new GeoNode(node("{}"));

		node.setLevelFromID(GradeSystem.french.indexOf("6b"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.french);

		assertEquals("6b", node.getKey("climbing:grade:french"));
		assertEquals(1, node.getTags().length());
	}

	@Test
	public void aNewGradeGoesUnderTheWikiKeyOfItsSystem() throws JSONException {
		GeoNode node = new GeoNode(node("{}"));

		node.setLevelFromID(GradeSystem.yds.indexOf("5.10c"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.yds);

		assertEquals("5.10c", node.getKey("climbing:grade:yds_class"));
		assertEquals(1, node.getTags().length());
	}

	@Test
	public void aGradePickedInASystemIsConvertedIntoTheOthers() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:uiaa\":\"7\",\"climbing:grade:french\":\"6b\","
				+ "\"climbing:grade:aid\":\"A1\"}"));

		node.setLevelFromID(GradeSystem.french.indexOf("6c"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.french);

		assertEquals("6c", node.getKey("climbing:grade:french"));
		assertEquals("7+", node.getKey("climbing:grade:uiaa"));
		assertEquals("A1", node.getKey("climbing:grade:aid"));
	}

	@Test
	public void keepingTheShownGradeInAnotherSystemAddsItsTag() throws JSONException {
		// UIAA 7 shows as French 6b.
		GeoNode node = new GeoNode(node("{\"climbing:grade:uiaa\":\"7\"}"));

		node.setLevelFromID(GradeSystem.french.indexOf("6b"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.french);

		assertEquals("7", node.getKey("climbing:grade:uiaa"));
		assertEquals("6b", node.getKey("climbing:grade:french"));
		assertEquals(2, node.getTags().length());
	}

	@Test
	public void keepingTheShownGradeLeavesTheSystemsOwnGradeAlone() throws JSONException {
		// French 6b+ is UIAA 7+: read from the UIAA tag, it would show as 6b and be rewritten.
		GeoNode node = new GeoNode(node("{\"climbing:grade:uiaa\":\"7\",\"climbing:grade:french\":\"6b+\"}"));

		node.setLevelFromID(GradeSystem.french.indexOf("6b+"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.french);

		assertEquals("7", node.getKey("climbing:grade:uiaa"));
		assertEquals("6b+", node.getKey("climbing:grade:french"));
		assertEquals(2, node.getTags().length());
	}

	@Test
	public void puttingBackTheLoadedGradesUndoesTheSystemsPickedSince() throws JSONException {
		GeoNode node = new GeoNode(node("{\"climbing:grade:uiaa\":\"7\",\"climbing:grade:uiaa:min\":\"5\","
				+ "\"name\":\"A\"}"));
		Map<String, String> loaded = node.getLevelTags(ClimbingTags.KEY_GRADE_TAG);

		node.setLevelFromID(GradeSystem.french.indexOf("6b"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.french);
		node.setLevelFromID(GradeSystem.yds.indexOf("5.10c"), ClimbingTags.KEY_GRADE_TAG,
				GradeSystem.yds);
		node.setLevelTags(ClimbingTags.KEY_GRADE_TAG, loaded);

		assertEquals("7", node.getKey("climbing:grade:uiaa"));
		assertEquals("5", node.getKey("climbing:grade:uiaa:min"));
		assertEquals("A", node.getKey(ClimbingTags.KEY_NAME));
		assertEquals(3, node.getTags().length());
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
