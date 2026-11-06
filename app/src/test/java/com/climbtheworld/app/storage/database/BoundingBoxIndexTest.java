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
 * The bounding box queries find what is in the box, and do it through their index, see OsmNode.
 */
@RunWith(RobolectricTestRunner.class)
public class BoundingBoxIndexTest {
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
	public void nodesOfTheTypesInTheBoxAreFound() throws Exception {
		database.osmNodeDao().insertNodesWithIgnore(Arrays.asList(
				node(1, 45.5, 24.5, "route_bottom"),
				node(2, 47, 24.5, "route_bottom"),
				node(3, 45.5, 26, "route_bottom"),
				node(4, 45.5, 24.5, "crag")));

		assertEquals(new HashSet<>(Arrays.asList(1L, 4L)), new HashSet<>(database.osmNodeDao()
				.loadBBox(46, 25, 45, 24, OsmEntity.EntityClimbingType.route,
						OsmEntity.EntityClimbingType.crag)));
		assertEquals(Arrays.asList(1L), database.osmNodeDao()
				.loadBBox(46, 25, 45, 24, OsmEntity.EntityClimbingType.route));
	}

	@Test
	public void nodesAreFoundThroughTheBoxIndexAlone() {
		String plan = queryPlan("SELECT osmID FROM OsmNode WHERE (localUpdateState != 'toDelete')"
				+ "AND (entityClimbingType IN (?,?)) AND (decimalLatitude BETWEEN ? AND ?) "
				+ "AND (decimalLongitude BETWEEN ? AND ?)");
		assertTrue(plan, plan.contains("COVERING INDEX index_OsmNode_box"));
	}

	@Test
	public void collectionsOverlappingTheBoxAreFound() throws Exception {
		database.osmCollectionDao().insertCollectionWithIgnore(Arrays.asList(
				collection(10, "crag", 45.2, 45.1, 24.1, 24.2),
				collection(11, "crag", 47.2, 47.1, 24.1, 24.2),
				collection(12, "area", 45.2, 45.1, 24.1, 24.2),
				// Across the antimeridian: from 179.5 east to 179.5 west.
				collection(13, "crag", 45.2, 45.1, 179.5, -179.5)));

		assertEquals(Arrays.asList(10L), database.osmCollectionDao()
				.loadBBox(46, 25, 45, 24, OsmEntity.EntityClimbingType.crag));
		assertEquals(Arrays.asList(13L), database.osmCollectionDao()
				.loadBBox(46, 180, 45, 179, OsmEntity.EntityClimbingType.crag));
	}

	@Test
	public void collectionsAreFoundThroughTheBoxIndexAlone() {
		String plan = queryPlan("SELECT osmID FROM OsmCollectionEntity WHERE "
				+ "(localUpdateState != 'toDelete') AND (entityClimbingType IN (?)) AND "
				+ OsmCollectionEntityDao.OVERLAPS_BOX);
		assertTrue(plan, plan.contains("COVERING INDEX index_OsmCollectionEntity_box"));
	}

	/** What SQLite plans for the query, as its EXPLAIN QUERY PLAN details. */
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

	private static OsmNode node(long id, double latitude, double longitude, String climbing)
			throws Exception {
		return new OsmNode(new JSONObject()
				.put("type", "node").put("id", id).put("lat", latitude).put("lon", longitude)
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", climbing)
						.put("name", "Node " + id)));
	}

	private static OsmCollectionEntity collection(long id, String climbing, double north,
	                                              double south, double west, double east)
			throws Exception {
		OsmCollectionEntity collection = new OsmCollectionEntity(new JSONObject()
				.put("type", "relation").put("id", id)
				// A crag or area relation is a site relation, see ClimbingFeature.
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", climbing)
						.put("type", "site").put("name", "Collection " + id)));
		collection.bBoxNorth = north;
		collection.bBoxSouth = south;
		collection.bBoxWest = west;
		collection.bBoxEast = east;
		return collection;
	}
}
