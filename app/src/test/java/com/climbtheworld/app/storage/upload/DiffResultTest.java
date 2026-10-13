package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class DiffResultTest {
	private static final String RESULT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<diffResult version=\"0.6\" generator=\"openstreetmap-cgimap\">\n"
			+ " <node old_id=\"-1\" new_id=\"1001\" new_version=\"1\"/>\n"
			+ " <relation old_id=\"-2\" new_id=\"2002\" new_version=\"1\"/>\n"
			+ " <relation old_id=\"10\" new_id=\"10\" new_version=\"4\"/>\n"
			+ " <node old_id=\"7\"/>\n"
			+ "</diffResult>";

	@Test
	public void readsNewIdsAndDeletes() throws Exception {
		DiffResult result = DiffResult.parse(RESULT);

		assertEquals(1001, result.newId("node", -1));
		assertEquals(2002, result.newId("relation", -2));
		assertEquals(10, result.newId("relation", 10));
		assertTrue(result.contains("node", 7));
		assertEquals(7, result.newId("node", 7));
		assertFalse(result.contains("node", 8));
		assertFalse(result.contains("relation", -1));
	}

	@Test
	public void remapsTheElementAndItsReferences() throws Exception {
		JSONObject crag = new JSONObject("{\"type\":\"relation\",\"id\":-2,\"members\":["
				+ "{\"type\":\"node\",\"ref\":-1,\"role\":\"\"},{\"type\":\"node\",\"ref\":\"5\",\"role\":\"\"},"
				+ "{\"type\":\"relation\",\"ref\":-1,\"role\":\"\"}]}");

		DiffResult.parse(RESULT).remap(crag);

		assertEquals(2002, crag.getLong("id"));
		assertEquals(1001, crag.getJSONArray("members").getJSONObject(0).getLong("ref"));
		assertEquals(5, crag.getJSONArray("members").getJSONObject(1).getLong("ref"));
		// A relation -1 was not in this upload.
		assertEquals(-1, crag.getJSONArray("members").getJSONObject(2).getLong("ref"));
	}

	@Test
	public void unreadableResultFails() {
		assertThrows(IOException.class, () -> DiffResult.parse("not xml"));
	}
}
