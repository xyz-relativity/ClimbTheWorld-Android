package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.Room;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The routes counted per country for the downloads screen: the nodes are read from the box index
 * alone and their countries looked up by the primary key of EntityCountry, see
 * EntityCountryDao.COUNT_PER_COUNTRY, with the same counts as the join on the type of the
 * OsmNode row it replaced.
 */
@RunWith(RobolectricTestRunner.class)
public class CountPerCountryTest {
	/** The query as it was, joined on the type of the OsmNode row, to compare the counts with. */
	private static final String JOINED_ON_NODE_TYPE = "SELECT countryIso, COUNT(*) AS entityCount "
			+ "FROM (SELECT EntityCountry.countryIso FROM EntityCountry JOIN OsmNode "
			+ "ON OsmNode.osmType = EntityCountry.osmType AND OsmNode.osmID = EntityCountry.osmID "
			+ "WHERE OsmNode.entityClimbingType = ? "
			+ "UNION ALL "
			+ "SELECT EntityCountry.countryIso FROM EntityCountry JOIN OsmCollectionEntity "
			+ "ON OsmCollectionEntity.osmType = EntityCountry.osmType "
			+ "AND OsmCollectionEntity.osmID = EntityCountry.osmID "
			+ "WHERE OsmCollectionEntity.entityClimbingType = ?) GROUP BY countryIso";

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
	public void nothingIsCountedInEmptyTables() {
		assertTrue(database.entityCountryDao()
				.countPerCountry(OsmEntity.EntityClimbingType.route).isEmpty());
	}

	@Test
	public void routesAreCountedInEachCountryThatHoldsThem() throws Exception {
		OsmNode changed = route(3);
		changed.localUpdateState = OsmEntity.EntityState.toUpdate;
		OsmNode deleted = route(4);
		deleted.localUpdateState = OsmEntity.EntityState.toDelete;
		OsmNode created = route(-1);
		created.localUpdateState = OsmEntity.EntityState.toUpdate;
		OsmNode createdUnlinked = route(-2);
		createdUnlinked.localUpdateState = OsmEntity.EntityState.toUpdate;
		database.osmNodeDao().insertNodesWithIgnore(Arrays.asList(route(1), route(2), changed,
				deleted, created, createdUnlinked, crag(6), crag(7), route(5)));
		// A way with the ID of a node, so a link counts only for the type it was made for.
		database.osmCollectionDao().insertCollectionWithIgnore(Arrays.asList(routeWay(5),
				routeWay(20)));
		database.entityCountryDao().insertAll(Arrays.asList(
				link(OsmEntity.EntityOsmType.node, 1, "RO"),
				// On the border, in both downloads.
				link(OsmEntity.EntityOsmType.node, 2, "RO"),
				link(OsmEntity.EntityOsmType.node, 2, "HU"),
				link(OsmEntity.EntityOsmType.node, 3, "RO"),
				link(OsmEntity.EntityOsmType.node, 4, "RO"),
				link(OsmEntity.EntityOsmType.node, -1, "RO"),
				link(OsmEntity.EntityOsmType.node, 6, "RO"),
				link(OsmEntity.EntityOsmType.node, 5, "HU"),
				link(OsmEntity.EntityOsmType.way, 5, "AT"),
				link(OsmEntity.EntityOsmType.way, 20, "RO"),
				// Of a node no longer stored.
				link(OsmEntity.EntityOsmType.node, 99, "RO"),
				// A country with crags only.
				link(OsmEntity.EntityOsmType.node, 7, "LI")));

		Map<String, Integer> expected = new HashMap<>();
		expected.put("RO", 6);
		expected.put("HU", 2);
		expected.put("AT", 1);
		assertEquals(expected, counts(OsmEntity.EntityClimbingType.route));

		for (OsmEntity.EntityClimbingType type : OsmEntity.EntityClimbingType.values()) {
			assertEquals(type.name(), joinedOnNodeType(type), counts(type));
		}
	}

	@Test
	public void nodesAreReadFromTheBoxIndexAloneAndTheirCountriesByKey() {
		String plan = queryPlan(EntityCountryDao.COUNT_PER_COUNTRY);
		assertTrue(plan, plan.contains("COVERING INDEX index_OsmNode_box (entityClimbingType=?)"));
		assertTrue(plan, plan.contains(
				"COVERING INDEX sqlite_autoindex_EntityCountry_1 (osmType=? AND osmID=?)"));
		// The nodes first, not each node link against all the nodes of the type.
		assertTrue(plan, plan.indexOf("index_OsmNode_box")
				< plan.indexOf("sqlite_autoindex_EntityCountry_1"));
		assertFalse(plan, plan.contains("sqlite_autoindex_EntityCountry_1 (osmType=?)"));
		assertFalse(plan, plan.contains("sqlite_autoindex_OsmNode_1"));
	}

	private Map<String, Integer> counts(OsmEntity.EntityClimbingType type) {
		Map<String, Integer> counts = new HashMap<>();
		for (EntityCountryDao.CountryEntityCount count : database.entityCountryDao()
				.countPerCountry(type)) {
			counts.put(count.countryIso, count.entityCount);
		}
		return counts;
	}

	private Map<String, Integer> joinedOnNodeType(OsmEntity.EntityClimbingType type) {
		Map<String, Integer> counts = new HashMap<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query(JOINED_ON_NODE_TYPE, new Object[]{type.name(), type.name()})) {
			while (cursor.moveToNext()) {
				counts.put(cursor.getString(0), cursor.getInt(1));
			}
		}
		return counts;
	}

	/** What SQLite plans for the query, as its EXPLAIN QUERY PLAN details. */
	private String queryPlan(String sql) {
		List<String> details = new ArrayList<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query("EXPLAIN QUERY PLAN " + sql.replace(":climbingType", "?"),
						new Object[]{"route", "route"})) {
			while (cursor.moveToNext()) {
				details.add(cursor.getString(cursor.getColumnIndexOrThrow("detail")));
			}
		}
		return String.join(" / ", details);
	}

	private static EntityCountry link(OsmEntity.EntityOsmType type, long id, String countryIso) {
		return new EntityCountry(type, id, countryIso);
	}

	private static OsmNode route(long id) throws Exception {
		return node(id, "route_bottom");
	}

	private static OsmNode crag(long id) throws Exception {
		return node(id, "crag");
	}

	private static OsmNode node(long id, String climbing) throws Exception {
		return new OsmNode(new JSONObject()
				.put("type", "node").put("id", id).put("lat", 45.5).put("lon", 24.5)
				.put("tags", new JSONObject().put("sport", "climbing")
						.put("climbing", climbing).put("name", "Node " + id)));
	}

	private static OsmCollectionEntity routeWay(long id) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("type", "way").put("id", id).put("nodes", new JSONArray().put(1).put(2))
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", "route")
						.put("name", "Way " + id)));
	}
}
