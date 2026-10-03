package com.climbtheworld.app.storage.logbook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class LogBookTest {
	private LogBookDatabase database;

	@Before
	public void setUp() {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),
						LogBookDatabase.class)
				.allowMainThreadQueries()
				.build();
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void entriesForDifferentOsmTypesWithTheSameIdAreKeptApart() {
		LogBookEntry routeEntry = new LogBookEntry(OsmEntity.EntityOsmType.node, 42L);
		routeEntry.note = "Crimpy start";
		routeEntry.attempt = LogBookEntry.Attempt.flashed;
		LogBookEntry cragEntry = new LogBookEntry(OsmEntity.EntityOsmType.relation, 42L);
		cragEntry.note = "Shady after 3pm";

		LogBook.save(database, routeEntry);
		LogBook.save(database, cragEntry);

		LogBookEntry storedRoute =
				database.logBookDao().find(OsmEntity.EntityOsmType.node, 42L);
		LogBookEntry storedCrag =
				database.logBookDao().find(OsmEntity.EntityOsmType.relation, 42L);
		assertEquals("Crimpy start", storedRoute.note);
		assertEquals(LogBookEntry.Attempt.flashed, storedRoute.attempt);
		assertEquals("Shady after 3pm", storedCrag.note);
		assertEquals(LogBookEntry.Attempt.none, storedCrag.attempt);
		assertNull(database.logBookDao().find(OsmEntity.EntityOsmType.way, 42L));
	}

	@Test
	public void anAttemptThatDidNotTopOutIsKeptEvenWithoutANote() {
		LogBookEntry entry = new LogBookEntry(OsmEntity.EntityOsmType.node, 7L);
		entry.attempt = LogBookEntry.Attempt.notCompleted;

		LogBook.save(database, entry);

		assertEquals(LogBookEntry.Attempt.notCompleted,
				database.logBookDao().find(OsmEntity.EntityOsmType.node, 7L).attempt);
	}

	@Test
	public void savingAnEntryWithNoNoteAndNoAttemptDeletesIt() {
		LogBookEntry entry = new LogBookEntry(OsmEntity.EntityOsmType.node, 7L);
		entry.attempt = LogBookEntry.Attempt.sent;
		LogBook.save(database, entry);
		assertTrue(entry.createdAt > 0);

		entry.attempt = LogBookEntry.Attempt.none;
		entry.note = "   ";
		LogBook.save(database, entry);

		assertNull(database.logBookDao().find(OsmEntity.EntityOsmType.node, 7L));
		assertEquals(0, entry.createdAt);
	}

	@Test
	public void loadingRefreshesTheStoredSnapshotWithoutChangingTheEditTime() throws Exception {
		LogBookEntry entry = new LogBookEntry(OsmEntity.EntityOsmType.node, 42L);
		entry.note = "Bring a 70m rope";
		entry.updateSnapshot(route(42L, "Old name", 45.0));
		LogBook.save(database, entry);
		long updatedAt = entry.updatedAt;

		LogBookEntry loaded = LogBook.load(database, OsmEntity.EntityOsmType.node,
				route(42L, "New name", 45.5));

		assertEquals("New name", loaded.name);
		assertEquals(GeoNode.NodeTypes.route.name(), loaded.nodeType);
		assertEquals(updatedAt, loaded.updatedAt);
		LogBookEntry stored = database.logBookDao().find(OsmEntity.EntityOsmType.node, 42L);
		assertEquals("New name", stored.name);
		assertEquals(45.5, stored.decimalLatitude, 0);
		assertEquals("Bring a 70m rope", stored.note);
	}

	@Test
	public void loadingAnElementWithoutAnEntryReturnsNull() throws Exception {
		assertNull(LogBook.load(database, OsmEntity.EntityOsmType.node,
				route(42L, "Unlogged", 45.0)));
	}

	@Test
	public void onlyElementsUploadedToOsmCanBeLogged() {
		assertTrue(LogBook.canLog(1L));
		assertFalse(LogBook.canLog(0L));
		assertFalse(LogBook.canLog(-1L));
	}

	private static GeoNode route(long osmID, String name, double latitude) throws Exception {
		return new GeoNode(new JSONObject()
				.put("id", osmID)
				.put("type", "node")
				.put("lat", latitude)
				.put("lon", 24)
				.put("tags", new JSONObject()
						.put("sport", "climbing")
						.put("climbing", "route_bottom")
						.put("name", name)));
	}
}
