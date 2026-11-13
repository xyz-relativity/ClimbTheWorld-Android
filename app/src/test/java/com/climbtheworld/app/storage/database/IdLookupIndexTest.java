package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
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
 * The queries that read nodes, ways and relations by their IDs find them, and do it through
 * the primary key rather than by reading the whole table, see OsmNodeDao.NODES_BY_ID.
 */
@RunWith(RobolectricTestRunner.class)
public class IdLookupIndexTest {
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
	public void nodesOfTheIdsAreFound() throws Exception {
		database.osmNodeDao().insertNodesWithIgnore(Arrays.asList(node(1), node(2), node(3)));

		Set<Long> found = new HashSet<>();
		// 4 is not stored.
		for (OsmNode node : database.osmNodeDao().resolveNodeData(Arrays.asList(1L, 3L, 4L))) {
			found.add(node.osmID);
		}

		assertEquals(new HashSet<>(Arrays.asList(1L, 3L)), found);
	}

	@Test
	public void nodesAreFoundThroughThePrimaryKey() {
		String plan = queryPlan(OsmNodeDao.NODES_BY_ID);
		assertTrue(plan, plan.contains("sqlite_autoindex_OsmNode_1 (osmType=? AND osmID=?)"));
	}

	@Test
	public void theWayAndTheRelationSharingAnIdAreBothFound() throws Exception {
		database.osmCollectionDao().insertCollectionWithIgnore(Arrays.asList(
				collection("way", 10), collection("relation", 10), collection("relation", 11)));

		Set<String> found = new HashSet<>();
		// 12 is not stored.
		for (OsmCollectionEntity collection
				: database.osmCollectionDao().resolveData(Arrays.asList(10L, 12L))) {
			found.add(collection.osmType.name() + collection.osmID);
		}

		assertEquals(new HashSet<>(Arrays.asList("way10", "relation10")), found);
	}

	@Test
	public void collectionsAreFoundThroughThePrimaryKey() {
		String plan = queryPlan(OsmCollectionEntityDao.COLLECTIONS_BY_ID);
		assertTrue(plan, plan.contains(
				"sqlite_autoindex_OsmCollectionEntity_1 (osmType=? AND osmID=?)"));
	}

	/**
	 * What SQLite plans for the query, as its EXPLAIN QUERY PLAN details.
	 *
	 * @param sql the query of a DAO, with its :ids as Room binds them
	 */
	private String queryPlan(String sql) {
		List<String> details = new ArrayList<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query("EXPLAIN QUERY PLAN " + sql.replace(":ids", "?,?,?"))) {
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

	private static OsmCollectionEntity collection(String type, long id) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("type", type).put("id", id)
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", "crag")
						.put("name", "Collection " + id)));
	}
}
