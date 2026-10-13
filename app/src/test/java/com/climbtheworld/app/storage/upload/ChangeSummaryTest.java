package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class ChangeSummaryTest {
	@Test
	public void listsChangedTagsMoveAndMembers() throws Exception {
		JSONObject base = new JSONObject("{\"lat\":1,\"lon\":2,\"tags\":{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"},"
				+ "\"members\":[{\"type\":\"node\",\"ref\":1,\"role\":\"\"}]}");
		JSONObject local = new JSONObject("{\"lat\":1.5,\"lon\":2,\"tags\":{\"a\":\"1\",\"b\":\"9\",\"d\":\"4\"},"
				+ "\"members\":[{\"type\":\"node\",\"ref\":2,\"role\":\"\"}]}");

		ChangeSummary summary = ChangeSummary.of(base, local);

		assertEquals(Arrays.asList("b", "c", "d"), summary.tags);
		assertTrue(summary.moved);
		assertTrue(summary.members);
	}

	@Test
	public void unchangedElementHasNothingToList() throws Exception {
		JSONObject element = new JSONObject("{\"lat\":1,\"lon\":2,\"tags\":{\"a\":\"1\"}}");

		ChangeSummary summary = ChangeSummary.of(element, new JSONObject(element.toString()));

		assertTrue(summary.tags.isEmpty());
		assertFalse(summary.moved);
		assertFalse(summary.members);
	}
}
