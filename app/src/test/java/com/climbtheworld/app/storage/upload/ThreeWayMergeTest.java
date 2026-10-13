package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class ThreeWayMergeTest {
	private static final String BASE = "{\"type\":\"node\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,"
			+ "\"tags\":{\"name\":\"A\",\"climbing:grade:uiaa\":\"6\",\"note\":\"x\"}}";

	@Test
	public void changesOnBothSidesOfDifferentTagsComeTogether() throws Exception {
		JSONObject local = json(BASE);
		local.getJSONObject("tags").put("name", "B");
		JSONObject server = json(BASE).put("version", 4);
		server.getJSONObject("tags").put("climbing:grade:uiaa", "7").remove("note");

		ThreeWayMerge.Result result = ThreeWayMerge.merge(json(BASE), local, server);

		assertTrue(result.conflicts.isEmpty());
		JSONObject tags = result.merged.getJSONObject("tags");
		assertEquals("B", tags.getString("name"));
		assertEquals("7", tags.getString("climbing:grade:uiaa"));
		assertFalse(tags.has("note"));
		assertFalse(result.merged.has("version"));
	}

	@Test
	public void sameTagChangedDifferentlyIsAConflictKeepingTheAppValue() throws Exception {
		JSONObject local = json(BASE);
		local.getJSONObject("tags").put("name", "Mine");
		JSONObject server = json(BASE);
		server.getJSONObject("tags").put("name", "Theirs");

		ThreeWayMerge.Result result = ThreeWayMerge.merge(json(BASE), local, server);

		assertEquals(Collections.singletonList("name"), result.conflicts);
		assertEquals("Mine", result.merged.getJSONObject("tags").getString("name"));
	}

	@Test
	public void sameChangeOnBothSidesIsNoConflict() throws Exception {
		JSONObject local = json(BASE);
		local.getJSONObject("tags").put("name", "Same");
		JSONObject server = json(BASE);
		server.getJSONObject("tags").put("name", "Same");

		assertTrue(ThreeWayMerge.merge(json(BASE), local, server).conflicts.isEmpty());
	}

	@Test
	public void positionComesFromTheSideThatMovedIt() throws Exception {
		JSONObject server = json(BASE).put("lat", 45.2);
		ThreeWayMerge.Result serverMoved = ThreeWayMerge.merge(json(BASE), json(BASE), server);
		assertEquals(45.2, serverMoved.merged.getDouble("lat"), 0);

		JSONObject local = json(BASE).put("lon", -73.3);
		ThreeWayMerge.Result bothMoved = ThreeWayMerge.merge(json(BASE), local, server);
		assertEquals(Collections.singletonList(ThreeWayMerge.POSITION), bothMoved.conflicts);
		assertEquals(45.1, bothMoved.merged.getDouble("lat"), 0);
		assertEquals(-73.3, bothMoved.merged.getDouble("lon"), 0);
	}

	@Test
	public void membersAreMergedAsOneList() throws Exception {
		String base = "{\"type\":\"relation\",\"id\":9,\"members\":[{\"type\":\"node\",\"ref\":\"1\",\"role\":\"\"}],\"tags\":{}}";
		JSONObject local = json("{\"type\":\"relation\",\"id\":9,\"members\":[{\"type\":\"node\",\"ref\":1,\"role\":\"\"},"
				+ "{\"type\":\"node\",\"ref\":-5,\"role\":\"\"}],\"tags\":{}}");
		JSONObject server = json(base).put("version", 2);

		ThreeWayMerge.Result result = ThreeWayMerge.merge(json(base), local, server);
		assertTrue(result.conflicts.isEmpty());
		assertEquals(2, result.merged.getJSONArray("members").length());

		JSONObject serverChanged = json("{\"type\":\"relation\",\"id\":9,\"members\":[{\"type\":\"node\",\"ref\":1,\"role\":\"\"},"
				+ "{\"type\":\"node\",\"ref\":7,\"role\":\"\"}],\"tags\":{}}");
		assertEquals(Collections.singletonList(ThreeWayMerge.MEMBERS),
				ThreeWayMerge.merge(json(base), local, serverChanged).conflicts);
	}

	@Test
	public void elementDeletedOnTheServerIsAConflict() throws Exception {
		JSONObject server = json("{\"type\":\"node\",\"id\":1,\"visible\":false,\"version\":5}");

		assertEquals(Collections.singletonList(ThreeWayMerge.DELETED),
				ThreeWayMerge.merge(json(BASE), json(BASE), server).conflicts);
	}

	@Test
	public void deleteNeedsTheServerToStillHaveTheDownloadedVersion() throws Exception {
		assertTrue(ThreeWayMerge.unchangedSince(json(BASE), json(BASE).put("version", 3)));
		JSONObject changed = json(BASE);
		changed.getJSONObject("tags").put("name", "Other");
		assertFalse(ThreeWayMerge.unchangedSince(json(BASE), changed));
		assertFalse(ThreeWayMerge.unchangedSince(json(BASE),
				json("{\"type\":\"node\",\"id\":1,\"visible\":false}")));
	}

	@Test
	public void conflictsListEveryPart() throws Exception {
		JSONObject local = json(BASE).put("lat", 45.3);
		local.getJSONObject("tags").put("name", "Mine").put("note", "y");
		JSONObject server = json(BASE).put("lat", 45.4);
		server.getJSONObject("tags").put("name", "Theirs").put("note", "z");

		assertEquals(Arrays.asList("name", "note", ThreeWayMerge.POSITION),
				ThreeWayMerge.merge(json(BASE), local, server).conflicts);
	}

	private static JSONObject json(String text) throws Exception {
		return new JSONObject(text);
	}
}
