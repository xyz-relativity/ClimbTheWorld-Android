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
		assertEquals(GradeSystem.vGrade.indexOf("V4/V5"),
				GeoNode.getLevelId(tags("climbing:grade:hueco", "v4/v5"), ClimbingTags.KEY_GRADE_TAG));
	}

	@Test
	public void unknownSystemIsSkipped() throws JSONException {
		JSONObject tags = tags("climbing:grade:polish", "VI.2", "climbing:grade:french", "6a");

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
				GeoNode.getLevelId(tags("climbing:grade:font", "6a"), ClimbingTags.KEY_GRADE_TAG));
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
		JSONObject tags = tags("climbing:grade:font:min", "6a", "climbing:grade:hueco:min", "V2");

		assertEquals(GradeSystem.vGrade.indexOf("V2"),
				GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG_MIN));
	}

	private static JSONObject tags(String... keyValues) throws JSONException {
		JSONObject result = new JSONObject();
		for (int i = 0; i < keyValues.length; i += 2) {
			result.put(keyValues[i], keyValues[i + 1]);
		}
		return result;
	}
}
