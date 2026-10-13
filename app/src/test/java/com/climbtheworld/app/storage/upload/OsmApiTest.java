package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class OsmApiTest {
	private FakeOsmServer server;
	private OsmApi api;

	@Before
	public void setUp() throws Exception {
		server = new FakeOsmServer();
		api = new OsmApi(server.apiUrl(), "token-1", "ClimbTheWorld/test");
	}

	@After
	public void tearDown() {
		server.close();
	}

	@Test
	public void writePermissionComesFromTheLoginsPermissions() throws Exception {
		server.answer("GET", "/permissions.json", 200,
				"{\"version\":\"0.6\",\"permissions\":[\"allow_read_prefs\",\"allow_write_api\"]}");
		server.answer("GET", "/permissions.json", 200, "{\"permissions\":[\"allow_read_prefs\"]}");

		assertTrue(api.canWrite());
		assertFalse(api.canWrite());
		assertEquals("Bearer token-1", server.call("GET", "/permissions.json").authorization);
	}

	@Test
	public void fetchReturnsCurrentVersionsByKey() throws Exception {
		server.answer("GET", "/nodes.json?nodes=1,2", 200, "{\"elements\":["
				+ "{\"type\":\"node\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,\"version\":3,\"tags\":{\"name\":\"A\"}},"
				+ "{\"type\":\"node\",\"id\":2,\"visible\":false,\"version\":5}]}");

		Map<String, JSONObject> current = api.fetch("node", Arrays.asList(1L, 2L));

		assertEquals(3, current.get("node|1").getLong("version"));
		assertFalse(current.get("node|2").getBoolean("visible"));
	}

	@Test
	public void changesetIsCreatedUploadedToAndClosed() throws Exception {
		server.answer("PUT", "/changeset/create", 200, "77");
		server.answer("POST", "/changeset/77/upload", 200,
				"<diffResult version=\"0.6\"><node old_id=\"-1\" new_id=\"900\" new_version=\"1\"/></diffResult>");
		server.answer("PUT", "/changeset/77/close", 200, "");
		Map<String, String> tags = new LinkedHashMap<>();
		tags.put("comment", "Test");

		long changeset = api.createChangeset(tags);
		DiffResult result = api.upload(changeset, "<osmChange/>");
		api.closeChangeset(changeset);

		assertEquals(77, changeset);
		assertEquals(900, result.newId("node", -1));
		assertEquals("<osm><changeset><tag k=\"comment\" v=\"Test\"/></changeset></osm>",
				server.call("PUT", "/changeset/create").body);
		assertEquals("<osmChange/>", server.call("POST", "/changeset/77/upload").body);
		assertEquals("Bearer token-1", server.call("PUT", "/changeset/77/close").authorization);
	}

	@Test
	public void errorAnswerTellsCodeAndOsmsExplanation() throws Exception {
		server.answer("POST", "/changeset/5/upload", 409,
				"Version mismatch: Provided 2, server had: 3 of Node 1");

		try {
			api.upload(5, "<osmChange/>");
			fail();
		} catch (OsmApi.OsmApiException e) {
			assertEquals(409, e.code);
			assertEquals("Version mismatch: Provided 2, server had: 3 of Node 1", e.getMessage());
		}
	}

	@Test
	public void manyIdsAreFetchedInChunks() throws Exception {
		Long[] ids = new Long[250];
		StringBuilder first = new StringBuilder();
		StringBuilder second = new StringBuilder();
		for (int index = 0; index < ids.length; index++) {
			ids[index] = (long) index + 1;
			(index < 200 ? first : second).append(index == 0 || index == 200 ? "" : ",").append(index + 1);
		}
		server.answer("GET", "/nodes.json?nodes=" + first, 200, "{\"elements\":[]}");
		server.answer("GET", "/nodes.json?nodes=" + second, 200, "{\"elements\":[{\"type\":\"node\",\"id\":250,\"version\":1}]}");

		assertEquals(Collections.singleton("node|250"), api.fetch("node", Arrays.asList(ids)).keySet());
	}
}
