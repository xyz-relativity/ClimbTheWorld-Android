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
import java.util.Collections;
import java.util.List;

/**
 * The searches by name find what holds the text, by name, and do it through the name index
 * rather than by taking every row apart, see OsmEntity.name.
 */
@RunWith(RobolectricTestRunner.class)
public class NameSearchTest {
	private static final OsmEntity.EntityClimbingType ROUTE = OsmEntity.EntityClimbingType.route;

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
	public void theNameIsTheNameTag() throws Exception {
		assertEquals("Big Crack", node(1, "Big Crack").name);
		assertEquals("", node(2, null).name);
	}

	@Test
	public void theNameFollowsTheTagsOnceRefreshed() throws Exception {
		OsmNode node = node(1, "Big Crack");

		node.getTags().put("name", "Small Crack");
		node.refreshColumns();

		assertEquals("Small Crack", node.name);
	}

	@Test
	public void nodesWhoseNameHoldsTheTextAreFoundByName() throws Exception {
		database.osmNodeDao().insertNodesWithIgnore(Arrays.asList(
				node(1, "Slab"), node(2, "Thin Crack"), node(3, "Big Crack"), node(4, null)));

		assertEquals(Arrays.asList(3L, 2L), nodeIds(database.osmNodeDao().find("crack", 10,
				ROUTE)));
		assertEquals(Collections.singletonList(3L), nodeIds(database.osmNodeDao().find("crack",
				1, ROUTE)));
		assertEquals(Collections.emptyList(), nodeIds(database.osmNodeDao().find("roof", 10,
				ROUTE)));
	}

	@Test
	public void namesWithASlashOrAQuoteAreFound() throws Exception {
		// JSON writes both with a backslash, which the name column does not.
		database.osmNodeDao().insertNodesWithIgnore(Collections.singletonList(
				node(1, "AC/DC \"live\"")));

		assertEquals(Collections.singletonList(1L), nodeIds(database.osmNodeDao().find(
				"c/d", 10, ROUTE)));
		assertEquals(Collections.singletonList(1L), nodeIds(database.osmNodeDao().find(
				"\"live\"", 10, ROUTE)));
	}

	@Test
	public void nodesOfOtherTypesAreLeftOut() throws Exception {
		database.osmNodeDao().insertNodesWithIgnore(Collections.singletonList(
				node(1, "Big Crack")));

		assertEquals(Collections.emptyList(), nodeIds(database.osmNodeDao().find("crack", 10,
				OsmEntity.EntityClimbingType.crag)));
	}

	@Test
	public void nodesAreSearchedThroughTheNameIndex() {
		String plan = queryPlan(OsmNodeDao.NODES_BY_NAME);
		assertTrue(plan, plan.contains("USING INDEX index_OsmNode_name"));
		// In the order of the index, so without sorting what was found.
		assertFalse(plan, plan.contains("B-TREE"));
	}

	@Test
	public void collectionsWhoseNameHoldsTheTextAreFoundByName() throws Exception {
		database.osmCollectionDao().insertCollectionWithIgnore(Arrays.asList(
				crag(10, "Upper Wall"), crag(11, "Lower Wall"), crag(12, "Boulders")));

		List<Long> found = new ArrayList<>();
		for (OsmCollectionEntity collection
				: database.osmCollectionDao().findCollections("wall", 10)) {
			found.add(collection.osmID);
		}

		assertEquals(Arrays.asList(11L, 10L), found);
	}

	@Test
	public void collectionsAreSearchedThroughTheNameIndex() {
		String plan = queryPlan(OsmCollectionEntityDao.COLLECTIONS_BY_NAME);
		assertTrue(plan, plan.contains("USING INDEX index_OsmCollectionEntity_name"));
		assertFalse(plan, plan.contains("B-TREE"));
	}

	/**
	 * What SQLite plans for the query, as its EXPLAIN QUERY PLAN details.
	 *
	 * @param sql the query of a DAO, with its parameters as Room binds them
	 */
	private String queryPlan(String sql) {
		List<String> details = new ArrayList<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query("EXPLAIN QUERY PLAN " + sql.replace(":types", "?,?")
						.replace(":searchString", "?").replace(":limit", "?"))) {
			while (cursor.moveToNext()) {
				details.add(cursor.getString(cursor.getColumnIndexOrThrow("detail")));
			}
		}
		return String.join(" / ", details);
	}

	private static List<Long> nodeIds(List<OsmNode> nodes) {
		List<Long> ids = new ArrayList<>();
		for (OsmNode node : nodes) {
			ids.add(node.osmID);
		}
		return ids;
	}

	/**
	 * @param name null for a route without a name
	 */
	private static OsmNode node(long id, String name) throws Exception {
		JSONObject tags = new JSONObject().put("sport", "climbing")
				.put("climbing", "route_bottom");
		if (name != null) {
			tags.put("name", name);
		}
		return new OsmNode(new JSONObject().put("type", "node").put("id", id)
				.put("lat", 45.5).put("lon", 24.5).put("tags", tags));
	}

	private static OsmCollectionEntity crag(long id, String name) throws Exception {
		OsmCollectionEntity crag = new OsmCollectionEntity(new JSONObject()
				.put("type", "relation").put("id", id)
				// A crag relation is a site relation, see ClimbingFeature.
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", "crag")
						.put("type", "site").put("name", name)));
		// Somewhere: a collection without a place is not found.
		crag.bBoxNorth = 45.6;
		crag.bBoxSouth = 45.4;
		crag.bBoxWest = 24.4;
		crag.bBoxEast = 24.6;
		return crag;
	}
}
