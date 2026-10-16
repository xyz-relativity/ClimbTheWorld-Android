package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.climbtheworld.app.converter.tools.GradeSystem;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GeoNodeGradeTest {
	@Test
	public void huecoIsAliasOfVGrade() {
		assertEquals(GradeSystem.vGrade, GradeSystem.fromString("hueco"));
		assertEquals("V Grade", GradeSystem.vGrade.getMainKey());
	}

	@Test
	public void huecoBeforeUiaaPrefersUiaa() throws JSONException {
		// OSM serves keys alphabetically, so hueco comes before uiaa.
		JSONObject tags = tags("climbing:grade:hueco", "V3", "climbing:grade:uiaa", "7+");

		assertNotEquals(GradeSystem.vGrade.indexOf("V3"), GradeSystem.uiaa.indexOf("7+"));
		assertEquals(GradeSystem.uiaa.indexOf("7+"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void huecoAloneResolves() throws JSONException {
		assertEquals(GradeSystem.vGrade.indexOf("V3"),
				GeoNode.getLevelId(tags("climbing:grade:hueco", "V3"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.vGrade.indexOf("V5"),
				GeoNode.getLevelId(tags("climbing:grade:hueco", "v4/v5"), ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void unknownSystemIsSkipped() throws JSONException {
		JSONObject tags = tags("climbing:grade:romanian", "VI.2", "climbing:grade:french", "6a");

		assertEquals(GradeSystem.french.indexOf("6a"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void unknownValueIsSkipped() throws JSONException {
		JSONObject tags = tags("climbing:grade:french", "6z", "climbing:grade:yds", "5.10a");

		assertEquals(GradeSystem.yds.indexOf("5.10a"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void singleInvalidValueIsUnknown() throws JSONException {
		assertEquals(-1,
				GeoNode.getLevelId(tags("climbing:grade:uiaa", "banana"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(-1,
				GeoNode.getLevelId(tags("climbing:grade:aid", "A2"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(-1, GeoNode.getLevelId(new JSONObject(), ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void minMaxKeysOnlyMatchTheirOwnTags() throws JSONException {
		JSONObject tags = tags(
				"climbing:grade:font:min", "6a",
				"climbing:grade:hueco:max", "V5",
				"climbing:grade:uiaa", "6",
				"climbing:grade:uiaa:max", "7",
				"climbing:grade:uiaa:min", "5");

		assertEquals(GradeSystem.uiaa.indexOf("6"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.uiaa.indexOf("5"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG_MIN));
		assertEquals(GradeSystem.uiaa.indexOf("7"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG_MAX));
	}

	@Test
	public void minFallsBackToNonStandardSystem() throws JSONException {
		JSONObject tags = tags("climbing:grade:aid:min", "A1", "climbing:grade:hueco:min", "V2");

		assertEquals(GradeSystem.vGrade.indexOf("V2"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG_MIN));
	}

	@Test
	public void osmKeysAreTheWikiOnes() {
		assertEquals(GradeSystem.nordic, GradeSystem.fromOsmKey("norwegian"));
		assertEquals(GradeSystem.ukTech, GradeSystem.fromOsmKey("british_technical"));
		assertEquals(GradeSystem.ukAdj, GradeSystem.fromOsmKey("british_adjectival"));
		assertEquals(GradeSystem.yds, GradeSystem.fromOsmKey("yds_class"));
		assertEquals(GradeSystem.vGrade, GradeSystem.fromOsmKey("hueco"));
		assertEquals(GradeSystem.polish, GradeSystem.fromOsmKey("polish"));
		assertEquals(GradeSystem.undef, GradeSystem.fromOsmKey("aid"));
		assertEquals(GradeSystem.undef, GradeSystem.fromOsmKey("ice"));
		assertEquals(GradeSystem.undef, GradeSystem.fromOsmKey("ewbank"));
		assertEquals(GradeSystem.undef, GradeSystem.fromOsmKey(""));
	}

	@Test
	public void fbTagIsFont() {
		assertEquals(GradeSystem.font, GradeSystem.fromOsmKey("fb"));
		assertEquals(GradeSystem.font, GradeSystem.fromOsmKey("fontainebleau"));
	}

	@Test
	public void everySystemReadsTheKeyItWrites() {
		for (GradeSystem system : GradeSystem.printableValues()) {
			assertEquals(system.name(), system, GradeSystem.fromOsmKey(system.getOsmKey()));
		}
		assertEquals("yds_class", GradeSystem.yds.getOsmKey());
		assertEquals("font", GradeSystem.font.getOsmKey());
	}

	@Test
	public void eachSystemReadsItsOwnGrade() throws JSONException {
		// French 6b+ is UIAA 7+, so the two tags disagree.
		JSONObject tags = tags("climbing:grade:french", "6b+", "climbing:grade:uiaa", "7");

		assertEquals(GradeSystem.french.indexOf("6b+"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG, GradeSystem.french));
		assertEquals(GradeSystem.uiaa.indexOf("7"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG, GradeSystem.uiaa));
		// Without a grade in YDS, the grade is read as without a system.
		assertEquals(GradeSystem.uiaa.indexOf("7"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
		assertEquals(GradeSystem.yds.indexOf("5.10a"), GeoNode.getLevelId(
				tags("climbing:grade:french", "6z", "climbing:grade:yds", "5.10a"),
				ClimbingTags.KEY_GRADE_TAG, GradeSystem.french));
	}

	@Test
	public void theGradeKeepsTheSystemItIsTaggedIn() throws JSONException {
		JSONObject tags = tags("climbing:grade:french", "6b", "climbing:grade:uiaa", "7");

		assertEquals(GradeSystem.french,
				GeoNode.getLevelSystem(tags, ClimbingTags.KEY_GRADE_TAG, GradeSystem.french));
		assertEquals(GradeSystem.uiaa,
				GeoNode.getLevelSystem(tags, ClimbingTags.KEY_GRADE_TAG, GradeSystem.uiaa));
		// Graded in two systems, neither the preferred one: the first of them in GradeSystem order.
		assertEquals(GradeSystem.uiaa,
				GeoNode.getLevelSystem(tags, ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
	}

	@Test
	public void theTaggedSystemDoesNotDependOnTheTagOrder() throws JSONException {
		assertEquals(GradeSystem.french, GeoNode.getLevelSystem(
				tags("climbing:grade:hueco", "V3", "climbing:grade:french", "6c+"),
				ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
		assertEquals(GradeSystem.french, GeoNode.getLevelSystem(
				tags("climbing:grade:french", "6c+", "climbing:grade:hueco", "V3"),
				ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
	}

	@Test
	public void anElementWithoutAReadableGradeTakesThePreferredSystem() throws JSONException {
		assertEquals(GradeSystem.yds,
				GeoNode.getLevelSystem(new JSONObject(), ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
		assertEquals(GradeSystem.yds, GeoNode.getLevelSystem(
				tags("climbing:grade:aid", "A1", "climbing:grade:french", "6z"),
				ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
		assertEquals(GradeSystem.yds, GeoNode.getLevelSystem(
				tags("climbing:grade:french:min", "6a"), ClimbingTags.KEY_GRADE_TAG, GradeSystem.yds));
	}

	@Test
	public void aRemovedSystemSettingFallsBackToUiaa() {
		assertEquals(GradeSystem.uiaa, GradeSystem.fromString("FB"));
		assertEquals(GradeSystem.uiaa, GradeSystem.fromString("Ewbank"));
		assertEquals(GradeSystem.uiaa, GradeSystem.fromString("WI"));
		assertEquals(GradeSystem.nordic, GradeSystem.fromString("Scandinavian"));
	}

	@Test
	public void everyTableCoversTheWholeScale() {
		for (GradeSystem system : GradeSystem.printableValues()) {
			assertEquals(system.name(), GradeSystem.maxGrades, system.data.length);
		}
	}

	@Test
	public void wikiGradesAreReadAsInTheComparisonTables() throws JSONException {
		// Wikipedia's Grade (climbing) tables: Font 7A+ is V7; Norwegian 7+ and Polish VI.3 are
		// 5.11d; British E2 is 6b.
		assertEquals(GradeSystem.vGrade.indexOf("V7"),
				GeoNode.getLevelId(tags("climbing:grade:fb", "7A+"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.vGrade.indexOf("V7"),
				GeoNode.getLevelId(tags("climbing:grade:font", "7a+"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.yds.indexOf("5.11d"),
				GeoNode.getLevelId(tags("climbing:grade:norwegian", "7+"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.yds.indexOf("5.11d"),
				GeoNode.getLevelId(tags("climbing:grade:polish", "VI.3"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.french.indexOf("6b"),
				GeoNode.getLevelId(tags("climbing:grade:british_adjectival", "E2"), ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void inBetweenGradesAreReadAsTheHarderOne() throws JSONException {
		assertEquals(GradeSystem.uiaa.indexOf("8-"),
				GeoNode.getLevelId(tags("climbing:grade:uiaa", "7+/8-"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(GradeSystem.french.indexOf("6c"),
				GeoNode.getLevelId(tags("climbing:grade:french", "6b+/6c"), ClimbingTags.KEY_GRADE_TAG));
		assertEquals(-1, GradeSystem.french.indexOf("6c++"));
	}

	private static JSONObject tags(String... keyValues) throws JSONException {
		JSONObject result = new JSONObject();
		for (int i = 0; i < keyValues.length; i += 2) {
			result.put(keyValues[i], keyValues[i + 1]);
		}
		return result;
	}
}
