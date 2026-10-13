package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityState;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.storage.offline.CountryBoxes;
import com.climbtheworld.app.storage.offline.importer.CountryOsmImporter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class OsmUploaderTest {
	private static final String CRAG_TAGS = "{\"type\":\"site\",\"site\":\"climbing\",\"sport\":\"climbing\","
			+ "\"climbing\":\"crag\",\"name\":\"Crag\"}";
	/** Routes 1 and 2 in crag 100. */
	private static final String DOWNLOAD = "{\"elements\":["
			+ "{\"type\":\"node\",\"id\":1,\"lat\":45.5,\"lon\":-73.6,\"tags\":{\"sport\":\"climbing\","
			+ "\"climbing\":\"route_bottom\",\"name\":\"One\"}},"
			+ "{\"type\":\"node\",\"id\":2,\"lat\":45.51,\"lon\":-73.61,\"tags\":{\"sport\":\"climbing\","
			+ "\"climbing\":\"route_bottom\",\"name\":\"Two\"}},"
			+ "{\"type\":\"relation\",\"id\":100,\"members\":[{\"type\":\"node\",\"ref\":1,\"role\":\"\"},"
			+ "{\"type\":\"node\",\"ref\":2,\"role\":\"\"}],\"tags\":" + CRAG_TAGS + "}]}";

	private AppDatabase database;
	private OsmEditStore store;
	private FakeOsmServer server;
	private OsmUploader uploader;

	@Before
	public void setUp() throws Exception {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
				.allowMainThreadQueries()
				.build();
		store = new OsmEditStore(database, CountryBoxes.read(new StringReader(
				"header\nCA,Canada,-141,41,-52,84\n")));
		new CountryOsmImporter(database).importResponse(new StringReader(DOWNLOAD), "CA");
		server = new FakeOsmServer();
		uploader = new OsmUploader(store, new OsmApi(server.apiUrl(), "token", "test"));
	}

	@After
	public void tearDown() {
		server.close();
		database.close();
	}

	@Test
	public void uploadMergesWithOsmAndGivesNewElementsTheirIds() throws Exception {
		OsmNode renamed = node(2);
		renamed.getTags().put("name", "Renamed");
		OsmNode added = store.newNode(45.52, -73.62);
		added.getTags().put("name", "New");
		OsmCollectionEntity crag = relation(100);
		RelationMembers.append(crag.getMembers(), "node", added.osmID, "");
		store.save(Arrays.asList(renamed, added), Collections.singletonList(crag));
		// Someone graded route 2 on OSM meanwhile.
		server.answer("GET", "/nodes.json?nodes=2", 200, "{\"elements\":[{\"type\":\"node\",\"id\":2,"
				+ "\"lat\":45.51,\"lon\":-73.61,\"version\":4,\"tags\":{\"sport\":\"climbing\","
				+ "\"climbing\":\"route_bottom\",\"name\":\"Two\",\"climbing:grade:uiaa\":\"6\"}}]}");
		server.answer("GET", "/relations.json?relations=100", 200, "{\"elements\":[{\"type\":\"relation\","
				+ "\"id\":100,\"version\":7,\"members\":[{\"type\":\"node\",\"ref\":1,\"role\":\"\"},"
				+ "{\"type\":\"node\",\"ref\":2,\"role\":\"\"}],\"tags\":" + CRAG_TAGS + "}]}");
		server.answer("PUT", "/changeset/create", 200, "11");
		server.answer("POST", "/changeset/11/upload", 200, "<diffResult>"
				+ "<node old_id=\"" + added.osmID + "\" new_id=\"5000\" new_version=\"1\"/>"
				+ "<node old_id=\"2\" new_id=\"2\" new_version=\"5\"/>"
				+ "<relation old_id=\"100\" new_id=\"100\" new_version=\"8\"/></diffResult>");
		server.answer("PUT", "/changeset/11/close", 200, "");

		OsmUploader.Prepared prepared = uploader.prepare(allChanges());
		assertTrue(prepared.conflicts.isEmpty());
		assertEquals(3, uploader.upload(prepared, "Added a route", "ClimbTheWorld test", status -> {
		}));

		String upload = server.call("POST", "/changeset/11/upload").body;
		assertTrue(upload.contains("<create><node id=\"" + added.osmID + "\" changeset=\"11\""));
		assertTrue(upload.contains("<node id=\"2\" changeset=\"11\" version=\"4\""));
		assertTrue(upload.contains("<tag k=\"climbing:grade:uiaa\" v=\"6\"/>"));
		assertTrue(upload.contains("<tag k=\"name\" v=\"Renamed\"/>"));
		assertTrue(upload.contains("<relation id=\"100\" changeset=\"11\" version=\"7\">"
				+ "<member type=\"node\" ref=\"1\" role=\"\"/><member type=\"node\" ref=\"2\" role=\"\"/>"
				+ "<member type=\"node\" ref=\"" + added.osmID + "\" role=\"\"/>"));
		assertTrue(server.call("PUT", "/changeset/create").body.contains("v=\"Added a route\""));
		assertNotNull(server.call("PUT", "/changeset/11/close"));

		assertNull(node(added.osmID));
		assertEquals(EntityState.clean, node(5000).localUpdateState);
		assertEquals("6", node(2).getTags().getString("climbing:grade:uiaa"));
		assertEquals("Renamed", node(2).getTags().getString("name"));
		assertEquals(Arrays.asList(1L, 2L, 5000L), relation(100).osmMembers);
		assertFalse(store.hasChanges());
	}

	@Test
	public void conflictCanBeSettledWithOsmsVersion() throws Exception {
		renameRoute2AndAnswerWithTheirName();

		OsmUploader.Prepared prepared = uploader.prepare(allChanges());
		assertEquals(Collections.singletonList("name"), prepared.conflicts.get("node|2"));
		uploader.useServer(prepared);

		assertEquals(0, uploader.upload(prepared, "Nothing", "test", status -> {
		}));
		assertNull(server.call("PUT", "/changeset/create"));
		assertEquals("Theirs", node(2).getTags().getString("name"));
		assertEquals(EntityState.clean, node(2).localUpdateState);
	}

	@Test
	public void conflictCanBeSettledWithTheAppsVersion() throws Exception {
		renameRoute2AndAnswerWithTheirName();
		server.answer("PUT", "/changeset/create", 200, "12");
		server.answer("POST", "/changeset/12/upload", 200,
				"<diffResult><node old_id=\"2\" new_id=\"2\" new_version=\"6\"/></diffResult>");
		server.answer("PUT", "/changeset/12/close", 200, "");

		OsmUploader.Prepared prepared = uploader.prepare(allChanges());
		uploader.keepLocal(prepared);
		uploader.upload(prepared, "Rename", "test", status -> {
		});

		String upload = server.call("POST", "/changeset/12/upload").body;
		assertTrue(upload.contains("<node id=\"2\" changeset=\"12\" version=\"5\""));
		assertTrue(upload.contains("<tag k=\"name\" v=\"Mine\"/>"));
		assertEquals("Mine", node(2).getTags().getString("name"));
		assertFalse(store.hasChanges());
	}

	@Test
	public void refusedUploadLeavesTheChangesAndClosesTheChangeset() throws Exception {
		OsmNode added = store.newNode(45.52, -73.62);
		store.saveNode(added);
		server.answer("PUT", "/changeset/create", 200, "13");
		server.answer("POST", "/changeset/13/upload", 400, "Element node/-1 has no tags");
		server.answer("PUT", "/changeset/13/close", 200, "");

		OsmUploader.Prepared prepared = uploader.prepare(allChanges());

		assertThrows(OsmApi.OsmApiException.class, () -> uploader.upload(prepared, "x", "test",
				status -> {
				}));
		assertNotNull(server.call("PUT", "/changeset/13/close"));
		assertNotNull(node(added.osmID));
		assertTrue(store.hasChanges());
	}

	private void renameRoute2AndAnswerWithTheirName() throws Exception {
		OsmNode renamed = node(2);
		renamed.getTags().put("name", "Mine");
		store.saveNode(renamed);
		server.answer("GET", "/nodes.json?nodes=2", 200, "{\"elements\":[{\"type\":\"node\",\"id\":2,"
				+ "\"lat\":45.51,\"lon\":-73.61,\"version\":5,\"tags\":{\"sport\":\"climbing\","
				+ "\"climbing\":\"route_bottom\",\"name\":\"Theirs\"}}]}");
	}

	private Set<String> allChanges() {
		Set<String> result = new HashSet<>();
		for (OsmEntity entity : store.loadChanges()) {
			result.add(RelationMembers.key(entity.osmType.name(), entity.osmID));
		}
		return result;
	}

	private OsmNode node(long osmId) {
		return database.osmNodeDao().find(EntityOsmType.node, osmId);
	}

	private OsmCollectionEntity relation(long osmId) {
		return database.osmCollectionDao().find(EntityOsmType.relation, osmId);
	}
}
