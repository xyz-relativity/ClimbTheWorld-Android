package com.climbtheworld.app.storage.edit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class OsmContentTest {
	@Test
	public void nodeWithSameTagsAndPositionIsTheSame() throws Exception {
		JSONObject first = new JSONObject("{\"type\":\"node\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,"
				+ "\"tags\":{\"name\":\"A\",\"climbing\":\"route_bottom\"}}");
		JSONObject second = new JSONObject("{\"id\":1,\"type\":\"node\",\"lon\":-73.2,\"lat\":45.1,"
				+ "\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"A\"},\"center\":{}}");

		assertTrue(OsmContent.sameContent(first, second));
	}

	@Test
	public void changedRemovedOrAddedTagIsDifferent() throws Exception {
		JSONObject base = new JSONObject("{\"tags\":{\"name\":\"A\",\"ele\":\"1234\"}}");

		assertFalse(OsmContent.sameContent(base,
				new JSONObject("{\"tags\":{\"name\":\"B\",\"ele\":\"1234\"}}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"tags\":{\"name\":\"A\"}}")));
		assertFalse(OsmContent.sameContent(base,
				new JSONObject("{\"tags\":{\"name\":\"A\",\"ele\":\"1234\",\"x\":\"y\"}}")));
		// Uploaded as text, so a number rewritten as 1234.0 is a change.
		assertFalse(OsmContent.sameContent(base,
				new JSONObject("{\"tags\":{\"name\":\"A\",\"ele\":1234.0}}")));
	}

	@Test
	public void missingAndEmptyTagsAreTheSame() throws Exception {
		assertTrue(OsmContent.sameContent(new JSONObject("{}"), new JSONObject("{\"tags\":{}}")));
	}

	@Test
	public void movedNodeIsDifferent() throws Exception {
		JSONObject base = new JSONObject("{\"lat\":45.1,\"lon\":-73.2}");

		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"lat\":45.1000001,\"lon\":-73.2}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"lat\":45.1}")));
	}

	@Test
	public void membersCompareInOrderWithTypeAndRole() throws Exception {
		JSONObject base = new JSONObject("{\"members\":["
				+ "{\"type\":\"node\",\"ref\":1,\"role\":\"\"},{\"type\":\"node\",\"ref\":2,\"role\":\"\"}]}");

		assertTrue(OsmContent.sameContent(base, new JSONObject("{\"members\":["
				+ "{\"type\":\"node\",\"ref\":1,\"role\":\"\"},{\"type\":\"node\",\"ref\":2,\"role\":\"\"}]}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"members\":["
				+ "{\"type\":\"node\",\"ref\":2,\"role\":\"\"},{\"type\":\"node\",\"ref\":1,\"role\":\"\"}]}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"members\":["
				+ "{\"type\":\"node\",\"ref\":1,\"role\":\"\"},{\"type\":\"way\",\"ref\":2,\"role\":\"\"}]}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"members\":["
				+ "{\"type\":\"node\",\"ref\":1,\"role\":\"\"},{\"type\":\"node\",\"ref\":2,\"role\":\"x\"}]}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"members\":["
				+ "{\"type\":\"node\",\"ref\":1,\"role\":\"\"}]}")));
	}

	@Test
	public void wayNodesCompareInOrder() throws Exception {
		JSONObject base = new JSONObject("{\"nodes\":[1,2,3]}");

		assertTrue(OsmContent.sameContent(base, new JSONObject("{\"nodes\":[1,2,3]}")));
		assertFalse(OsmContent.sameContent(base, new JSONObject("{\"nodes\":[1,3,2]}")));
	}
}
