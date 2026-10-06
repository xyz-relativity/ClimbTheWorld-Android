package com.climbtheworld.app.storage.logbook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import androidx.room.Room;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class LogBookBackupTest {
	private LogBookDatabase source;
	private LogBookDatabase target;

	@Before
	public void setUp() {
		source = newDatabase();
		target = newDatabase();
	}

	@After
	public void tearDown() {
		source.close();
		target.close();
	}

	@Test
	public void aSavedLogBookIsRestoredWithAllItsFields() throws Exception {
		LogBookEntry route = entry(OsmEntity.EntityOsmType.node, 42L, "Été indien", 100L);
		route.attempt = LogBookEntry.Attempt.onsight;
		route.createdAt = 50L;
		route.name = "Le Toit";
		route.nodeType = "route";
		route.decimalLatitude = 45.5;
		route.decimalLongitude = 24.25;
		source.logBookDao().upsert(route);
		// An entry whose element was never viewed has no snapshot.
		source.logBookDao().upsert(entry(OsmEntity.EntityOsmType.relation, 42L, "Shady", 200L));

		ByteArrayOutputStream file = new ByteArrayOutputStream();
		assertEquals(2, LogBookBackup.write(source, file));
		LogBookBackup.RestoreResult result = restore(target, file.toByteArray());

		assertEquals(2, result.added);
		assertEquals(0, result.updated);
		assertEquals(0, result.unchanged);
		LogBookEntry restored = target.logBookDao().find(OsmEntity.EntityOsmType.node, 42L);
		assertEquals("Été indien", restored.note);
		assertEquals(LogBookEntry.Attempt.onsight, restored.attempt);
		assertEquals(50L, restored.createdAt);
		assertEquals(100L, restored.updatedAt);
		assertEquals("Le Toit", restored.name);
		assertEquals("route", restored.nodeType);
		assertEquals(45.5, restored.decimalLatitude, 0);
		assertEquals(24.25, restored.decimalLongitude, 0);
		LogBookEntry crag = target.logBookDao().find(OsmEntity.EntityOsmType.relation, 42L);
		assertEquals("Shady", crag.note);
		assertNull(crag.name);
		assertNull(crag.nodeType);
	}

	@Test
	public void restoringKeepsTheMostRecentlyEditedVersionOfEachEntry() throws Exception {
		source.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 1L, "newer in file", 200L));
		source.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 2L, "older in file", 100L));
		source.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 3L, "same", 100L));
		target.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 1L, "older stored", 100L));
		target.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 2L, "newer stored", 200L));
		target.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 3L, "same", 100L));
		target.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 4L, "only stored", 100L));

		LogBookBackup.RestoreResult result = restore(target, save(source));

		assertEquals(0, result.added);
		assertEquals(1, result.updated);
		assertEquals(2, result.unchanged);
		assertEquals("newer in file", note(target, 1L));
		assertEquals("newer stored", note(target, 2L));
		assertEquals("same", note(target, 3L));
		assertEquals("only stored", note(target, 4L));
	}

	@Test
	public void entriesThatCannotBeLoggedAreNotRestored() throws Exception {
		source.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, -5L, "not uploaded", 1L));
		source.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 6L, "", 1L));

		LogBookBackup.RestoreResult result = restore(target, save(source));

		assertEquals(0, result.added);
		assertEquals(2, result.unchanged);
		assertEquals(0, target.logBookDao().loadAll().size());
	}

	@Test
	public void anAttemptFromANewerVersionOfTheAppIsDroppedButTheNoteIsKept() throws Exception {
		JSONObject file = LogBookBackup.toJson(List.of(
				entry(OsmEntity.EntityOsmType.node, 7L, "Bring a 70m rope", 1L)), 0);
		file.getJSONArray("entries").getJSONObject(0).put("attempt", "topRope");

		restore(target, file.toString().getBytes(StandardCharsets.UTF_8));

		LogBookEntry restored = target.logBookDao().find(OsmEntity.EntityOsmType.node, 7L);
		assertEquals("Bring a 70m rope", restored.note);
		assertEquals(LogBookEntry.Attempt.none, restored.attempt);
	}

	@Test
	public void aFileThatIsNotABackupIsRejected() {
		assertRejected("{\"entries\": []}", R.string.log_book_restore_not_a_backup);
		assertRejected("not json", R.string.log_book_restore_not_a_backup);
		assertRejected("{\"format\": \"climbtheworld.logbook\", \"version\": 1, \"entries\": "
						+ "[{\"osmType\": \"area\", \"osmID\": 1}]}",
				R.string.log_book_restore_not_a_backup);
	}

	@Test
	public void aBackupFromANewerFormatIsRejected() {
		assertRejected("{\"format\": \"climbtheworld.logbook\", \"version\": 2, \"entries\": []}",
				R.string.log_book_restore_newer_version);
	}

	@Test
	public void aRejectedFileChangesNothing() throws Exception {
		target.logBookDao().upsert(entry(OsmEntity.EntityOsmType.node, 1L, "kept", 100L));

		assertRejected("{\"format\": \"climbtheworld.logbook\", \"version\": 1, \"entries\": "
						+ "[{\"osmType\": \"node\", \"osmID\": 1, \"note\": \"new\", "
						+ "\"updatedAt\": 200}, {\"osmType\": \"area\", \"osmID\": 2}]}",
				R.string.log_book_restore_not_a_backup);

		assertEquals("kept", note(target, 1L));
	}

	@Test
	public void theFileNameHasTheDate() {
		assertEquals("climbtheworld-logbook-",
				LogBookBackup.getFileName(0).substring(0, 22));
		assertEquals(".json", LogBookBackup.getFileName(0).substring(32));
	}

	private void assertRejected(String file, int messageId) {
		try {
			restore(target, file.getBytes(StandardCharsets.UTF_8));
			fail("expected the file to be rejected");
		} catch (LogBookBackup.InvalidBackupException exception) {
			assertEquals(messageId, exception.getMessageId());
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private static LogBookDatabase newDatabase() {
		return Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),
						LogBookDatabase.class)
				.allowMainThreadQueries()
				.build();
	}

	private static LogBookEntry entry(OsmEntity.EntityOsmType osmType, long osmID, String note,
	                                  long updatedAt) {
		LogBookEntry entry = new LogBookEntry(osmType, osmID);
		entry.note = note;
		entry.updatedAt = updatedAt;
		return entry;
	}

	private static byte[] save(LogBookDatabase database) throws Exception {
		ByteArrayOutputStream file = new ByteArrayOutputStream();
		LogBookBackup.write(database, file);
		return file.toByteArray();
	}

	private static LogBookBackup.RestoreResult restore(LogBookDatabase database, byte[] file)
			throws Exception {
		return LogBookBackup.restore(database, new ByteArrayInputStream(file));
	}

	private static String note(LogBookDatabase database, long osmID) {
		return database.logBookDao().find(OsmEntity.EntityOsmType.node, osmID).note;
	}
}
