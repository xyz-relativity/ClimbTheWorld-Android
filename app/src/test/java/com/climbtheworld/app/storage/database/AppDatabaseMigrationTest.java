package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Opening a version 4 database with the app's schema migrates it: Room then checks the tables
 * and indexes against the entities, so a migration leaving them apart fails to open.
 */
@RunWith(RobolectricTestRunner.class)
public class AppDatabaseMigrationTest {
	private static final String NAME = "migration-test.db";
	private final Context context = RuntimeEnvironment.getApplication();

	@Before
	@After
	public void deleteDatabase() {
		context.deleteDatabase(NAME);
	}

	@Test
	public void version4KeepsItsDataAndGetsTheBoxIndexes() throws Exception {
		// The current schema, holding a node and a crag, taken back to version 4: its position
		// indexes in place of the box ones.
		AppDatabase current = open();
		current.osmNodeDao().insertNodesWithIgnore(Collections.singletonList(
				new OsmNode(new JSONObject()
						.put("type", "node").put("id", 1).put("lat", 45.5).put("lon", 24.5)
						.put("tags", new JSONObject().put("sport", "climbing")
								.put("climbing", "route_bottom").put("name", "Route")))));
		OsmCollectionEntity crag = new OsmCollectionEntity(new JSONObject()
				.put("type", "relation").put("id", 10)
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", "crag")
						.put("type", "site").put("name", "Crag")));
		crag.bBoxNorth = 45.6;
		crag.bBoxSouth = 45.4;
		crag.bBoxWest = 24.4;
		crag.bBoxEast = 24.6;
		current.osmCollectionDao().insertCollectionWithIgnore(Collections.singletonList(crag));
		current.close();
		SQLiteDatabase old = SQLiteDatabase.openDatabase(
				context.getDatabasePath(NAME).getPath(), null, SQLiteDatabase.OPEN_READWRITE);
		old.execSQL("DROP INDEX index_OsmNode_box");
		old.execSQL("CREATE INDEX index_OsmNode_decimalLatitude ON OsmNode (decimalLatitude)");
		old.execSQL("CREATE INDEX index_OsmNode_decimalLongitude ON OsmNode (decimalLongitude)");
		old.execSQL("DROP INDEX index_OsmCollectionEntity_box");
		for (String edge : new String[]{"bBoxNorth", "bBoxEast", "bBoxSouth", "bBoxWest"}) {
			old.execSQL("CREATE INDEX index_OsmCollectionEntity_" + edge
					+ " ON OsmCollectionEntity (" + edge + ")");
		}
		old.execSQL("PRAGMA user_version = 4");
		old.close();

		AppDatabase migrated = open();
		assertEquals(Collections.singletonList(1L), migrated.osmNodeDao()
				.loadBBox(46, 25, 45, 24, OsmEntity.EntityClimbingType.route));
		assertEquals(Collections.singletonList(10L), migrated.osmCollectionDao()
				.loadBBox(46, 25, 45, 24, OsmEntity.EntityClimbingType.crag));
		assertEquals(Collections.singleton("index_OsmNode_box"), indexes(migrated, "OsmNode"));
		assertEquals(Collections.singleton("index_OsmCollectionEntity_box"),
				indexes(migrated, "OsmCollectionEntity"));
		migrated.close();
	}

	private AppDatabase open() {
		return Room.databaseBuilder(context, AppDatabase.class, NAME)
				.addMigrations(AppDatabase.MIGRATION_4_5)
				.allowMainThreadQueries()
				.build();
	}

	/** The names of the table's indexes, those of its primary key left out. */
	private static Set<String> indexes(AppDatabase database, String table) {
		Set<String> names = new HashSet<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query("PRAGMA index_list(" + table + ")")) {
			while (cursor.moveToNext()) {
				String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
				if (!name.startsWith("sqlite_autoindex")) {
					names.add(name);
				}
			}
		}
		return names;
	}
}
