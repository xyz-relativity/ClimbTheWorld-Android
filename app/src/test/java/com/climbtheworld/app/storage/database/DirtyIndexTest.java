package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.Room;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The changes waiting for upload, of every state but clean, are counted and loaded through the
 * state index rather than by reading the whole table, see OsmNodeDao.DIRTY, and the deletes of
 * clean rows keep off that index.
 */
@RunWith(RobolectricTestRunner.class)
public class DirtyIndexTest {
	private AppDatabase database;

	@Before
	public void setUp() {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),
				AppDatabase.class).allowMainThreadQueries().build();
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void nothingIsDirtyInEmptyTables() {
		assertEquals(0, database.osmNodeDao().countDirty());
		assertEquals(0, database.osmCollectionDao().countDirty());
		assertTrue(database.osmNodeDao().loadDirty().isEmpty());
		assertTrue(database.osmCollectionDao().loadDirty().isEmpty());
	}

	@Test
	public void nodesOfEveryStateButCleanAreDirty() throws Exception {
		List<OsmNode> nodes = new ArrayList<>();
		Set<Long> dirty = new HashSet<>();
		for (OsmEntity.EntityState state : OsmEntity.EntityState.values()) {
			OsmNode node = node(nodes.size() + 1);
			node.localUpdateState = state;
			nodes.add(node);
			if (state != OsmEntity.EntityState.clean) {
				dirty.add(node.osmID);
			}
		}
		database.osmNodeDao().insertNodesWithIgnore(nodes);

		assertEquals(dirty.size(), database.osmNodeDao().countDirty());
		Set<Long> loaded = new HashSet<>();
		for (OsmNode node : database.osmNodeDao().loadDirty()) {
			loaded.add(node.osmID);
		}
		assertEquals(dirty, loaded);
	}

	@Test
	public void collectionsOfEveryStateButCleanAreDirty() throws Exception {
		List<OsmCollectionEntity> collections = new ArrayList<>();
		Set<Long> dirty = new HashSet<>();
		for (OsmEntity.EntityState state : OsmEntity.EntityState.values()) {
			OsmCollectionEntity collection = collection(collections.size() + 10);
			collection.localUpdateState = state;
			collections.add(collection);
			if (state != OsmEntity.EntityState.clean) {
				dirty.add(collection.osmID);
			}
		}
		database.osmCollectionDao().insertCollectionWithIgnore(collections);

		assertEquals(dirty.size(), database.osmCollectionDao().countDirty());
		Set<Long> loaded = new HashSet<>();
		for (OsmCollectionEntity collection : database.osmCollectionDao().loadDirty()) {
			loaded.add(collection.osmID);
		}
		assertEquals(dirty, loaded);
	}

	@Test
	public void cleanRowsAloneAreNotDirty() throws Exception {
		database.osmNodeDao().insertNodesWithIgnore(Arrays.asList(node(1), node(2)));
		database.osmCollectionDao().insertCollectionWithIgnore(Arrays.asList(collection(10)));

		assertEquals(0, database.osmNodeDao().countDirty());
		assertEquals(0, database.osmCollectionDao().countDirty());
	}

	@Test
	public void dirtyRowsAreFoundThroughTheStateIndex() {
		for (String sql : new String[]{OsmNodeDao.COUNT_DIRTY, OsmNodeDao.LOAD_DIRTY}) {
			String plan = queryPlan(sql);
			assertTrue(plan, plan.contains("index_OsmNode_localUpdateState (localUpdateState<?)"));
			assertTrue(plan, plan.contains("index_OsmNode_localUpdateState (localUpdateState>?)"));
		}
		for (String sql : new String[]{OsmCollectionEntityDao.COUNT_DIRTY,
				OsmCollectionEntityDao.LOAD_DIRTY}) {
			String plan = queryPlan(sql);
			assertTrue(plan, plan.contains(
					"index_OsmCollectionEntity_localUpdateState (localUpdateState<?)"));
			assertTrue(plan, plan.contains(
					"index_OsmCollectionEntity_localUpdateState (localUpdateState>?)"));
		}
	}

	@Test
	public void unownedCleanRowsAreDeletedWithoutTheStateIndex() {
		// Nearly every row is clean: walking the index would look each one up in the table.
		String nodes = queryPlan(OsmNodeDao.DELETE_UNOWNED_CLEAN);
		assertFalse(nodes, nodes.contains("index_OsmNode_localUpdateState"));
		String collections = queryPlan(OsmCollectionEntityDao.DELETE_UNOWNED_CLEAN);
		assertFalse(collections, collections.contains("index_OsmCollectionEntity_localUpdateState"));
	}

	@Test
	public void unownedCleanRowsAreDeletedAndTheRestKept() throws Exception {
		OsmNode changed = node(2);
		changed.localUpdateState = OsmEntity.EntityState.toUpdate;
		database.osmNodeDao().insertNodesWithIgnore(Arrays.asList(node(1), changed, node(3)));
		database.entityCountryDao().insertAll(Arrays.asList(
				new EntityCountry(OsmEntity.EntityOsmType.node, 3, "RO")));

		database.osmNodeDao().deleteUnownedCleanNodes();

		Set<Long> kept = new HashSet<>();
		for (OsmNode node : database.osmNodeDao().resolveNodeData(Arrays.asList(1L, 2L, 3L))) {
			kept.add(node.osmID);
		}
		assertEquals(new HashSet<>(Arrays.asList(2L, 3L)), kept);
	}

	/** What SQLite plans for the statement, as its EXPLAIN QUERY PLAN details. */
	private String queryPlan(String sql) {
		List<String> details = new ArrayList<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query("EXPLAIN QUERY PLAN " + sql)) {
			while (cursor.moveToNext()) {
				details.add(cursor.getString(cursor.getColumnIndexOrThrow("detail")));
			}
		}
		return String.join(" / ", details);
	}

	private static OsmNode node(long id) throws Exception {
		return new OsmNode(new JSONObject()
				.put("type", "node").put("id", id).put("lat", 45.5).put("lon", 24.5)
				.put("tags", new JSONObject().put("sport", "climbing")
						.put("climbing", "route_bottom").put("name", "Node " + id)));
	}

	private static OsmCollectionEntity collection(long id) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("type", "relation").put("id", id)
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", "crag")
						.put("name", "Collection " + id)));
	}
}
