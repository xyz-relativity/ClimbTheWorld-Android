package com.climbtheworld.app.storage.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class FormChangesTest {
	private static final String ORIGINAL = "{\"type\":\"node\",\"id\":7,\"lat\":45.1234567,\"lon\":-73.7654321,"
			+ "\"tags\":{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\"Old\","
			+ "\"climbing:grade:french\":\"6a\",\"website\":\"https://a.example\",\"ele\":\"1234\","
			+ "\"climbing:sport\":\"3\",\"note\":\"unknown to the form\"}}";
	/** What the form writes for ORIGINAL when nothing is changed in it. */
	private static final String UNTOUCHED = "{\"type\":\"node\",\"id\":7,\"lat\":45.123457,\"lon\":-73.765432,"
			+ "\"tags\":{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\"Old\","
			+ "\"climbing:grade:uiaa\":\"6+\",\"website\":\"https://a.example\","
			+ "\"contact:website\":\"https://a.example\",\"ele\":1234.0,"
			+ "\"climbing:sport\":\"yes\",\"note\":\"unknown to the form\"}}";

	@Test
	public void untouchedFormChangesNothing() throws Exception {
		JSONObject result = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), json(UNTOUCHED));

		assertTrue(OsmContent.sameContent(json(ORIGINAL), result));
		assertEquals(45.1234567, result.getDouble("lat"), 0);
	}

	@Test
	public void onlyTheChangedFieldIsWritten() throws Exception {
		JSONObject edited = json(UNTOUCHED);
		edited.getJSONObject("tags").put("name", "New");

		JSONObject tags = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), edited).getJSONObject("tags");

		assertEquals("New", tags.getString("name"));
		assertEquals("6a", tags.getString("climbing:grade:french"));
		assertFalse(tags.has("climbing:grade:uiaa"));
		assertFalse(tags.has("contact:website"));
		assertEquals("1234", tags.getString("ele"));
		assertEquals("3", tags.getString("climbing:sport"));
		assertEquals("unknown to the form", tags.getString("note"));
	}

	@Test
	public void changedGradeReplacesTheGradeInEverySystem() throws Exception {
		JSONObject edited = json(UNTOUCHED);
		edited.getJSONObject("tags").put("climbing:grade:uiaa", "7-");

		JSONObject tags = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), edited).getJSONObject("tags");

		assertEquals("7-", tags.getString("climbing:grade:uiaa"));
		assertFalse(tags.has("climbing:grade:french"));
	}

	@Test
	public void gradeRangesAreSeparateFromTheGrade() throws Exception {
		JSONObject original = json("{\"tags\":{\"climbing:grade:french\":\"6a\","
				+ "\"climbing:grade:french:min\":\"5a\",\"climbing:grade:french:max\":\"7a\"}}");
		JSONObject untouched = json("{\"tags\":{\"climbing:grade:uiaa\":\"6+\","
				+ "\"climbing:grade:uiaa:min\":\"5\",\"climbing:grade:uiaa:max\":\"8-\"}}");
		JSONObject edited = json(untouched.toString());
		edited.getJSONObject("tags").put("climbing:grade:uiaa:max", "8");

		JSONObject tags = FormChanges.resolve(original, untouched, edited).getJSONObject("tags");

		assertEquals("8", tags.getString("climbing:grade:uiaa:max"));
		assertFalse(tags.has("climbing:grade:french:max"));
		assertEquals("6a", tags.getString("climbing:grade:french"));
		assertEquals("5a", tags.getString("climbing:grade:french:min"));
	}

	@Test
	public void clearedFieldRemovesTheTag() throws Exception {
		JSONObject edited = json(UNTOUCHED);
		edited.getJSONObject("tags").remove("website");
		edited.getJSONObject("tags").remove("contact:website");

		JSONObject tags = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), edited).getJSONObject("tags");

		assertFalse(tags.has("website"));
		assertFalse(tags.has("contact:website"));
	}

	@Test
	public void toggledStyleKeepsTheOtherStyles() throws Exception {
		JSONObject edited = json(UNTOUCHED);
		edited.getJSONObject("tags").put("climbing:trad", "yes");

		JSONObject tags = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), edited).getJSONObject("tags");

		assertEquals("yes", tags.getString("climbing:trad"));
		assertEquals("3", tags.getString("climbing:sport"));
	}

	@Test
	public void movedNodeTakesTheNewPosition() throws Exception {
		JSONObject edited = json(UNTOUCHED);
		edited.put("lat", 45.2);
		edited.put("lon", -73.8);

		JSONObject result = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), edited);

		assertEquals(45.2, result.getDouble("lat"), 0);
		assertEquals(-73.8, result.getDouble("lon"), 0);
		assertEquals("Old", result.getJSONObject("tags").getString("name"));
	}

	@Test
	public void changedTypeRewritesTheTypeTags() throws Exception {
		JSONObject edited = json(UNTOUCHED);
		edited.getJSONObject("tags").put("climbing", "crag");

		JSONObject tags = FormChanges.resolve(json(ORIGINAL), json(UNTOUCHED), edited).getJSONObject("tags");

		assertEquals("crag", tags.getString("climbing"));
		assertEquals("climbing", tags.getString("sport"));
	}

	private static JSONObject json(String text) throws Exception {
		return new JSONObject(text);
	}
}
