package com.climbtheworld.app.storage.database;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {
		OsmNode.class,
		OsmCollectionEntity.class,
		EntityCountry.class,
		DownloadedCountry.class
}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
	private static final String OSM_CACHE_DB = "offlineClimbingData.db";
	/**
	 * Version 5 indexes the nodes and the collections for the bounding box queries, see OsmNode,
	 * in place of the index each had on every position column. The downloads and the edits
	 * stay. The indexes are made as Room makes them, which checks them once migrated.
	 */
	static final Migration MIGRATION_4_5 = new Migration(4, 5) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			database.execSQL("DROP INDEX IF EXISTS index_OsmNode_decimalLatitude");
			database.execSQL("DROP INDEX IF EXISTS index_OsmNode_decimalLongitude");
			database.execSQL("CREATE INDEX IF NOT EXISTS index_OsmNode_box ON OsmNode "
					+ "(entityClimbingType, decimalLatitude, decimalLongitude, localUpdateState, "
					+ "osmID)");
			database.execSQL("DROP INDEX IF EXISTS index_OsmCollectionEntity_bBoxNorth");
			database.execSQL("DROP INDEX IF EXISTS index_OsmCollectionEntity_bBoxEast");
			database.execSQL("DROP INDEX IF EXISTS index_OsmCollectionEntity_bBoxSouth");
			database.execSQL("DROP INDEX IF EXISTS index_OsmCollectionEntity_bBoxWest");
			database.execSQL("CREATE INDEX IF NOT EXISTS index_OsmCollectionEntity_box ON "
					+ "OsmCollectionEntity (entityClimbingType, bBoxNorth, bBoxSouth, bBoxWest, "
					+ "bBoxEast, localUpdateState, osmID)");
		}
	};
	// The name tag as the stored element's text has it, between the quotes that follow its key,
	// empty without the key: right for all but the names holding a character JSON writes with
	// a backslash.
	private static final String NAME_IN_JSON_TEXT =
			"CASE WHEN INSTR(jsonNodeInfo, '\"name\":\"') = 0 THEN '' ELSE "
					+ "SUBSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), 0, "
					+ "INSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), '\"')) "
					+ "END";
	/**
	 * Version 6 gives the nodes and the collections their name as a column, with an index, to
	 * search by name through, see OsmEntity.name. The downloads and the edits stay. The names
	 * are taken out of the stored elements in one statement a table, as the searches did until
	 * then, and the few that this gets wrong are read again from the element itself.
	 */
	static final Migration MIGRATION_5_6 = new Migration(5, 6) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			for (String table : new String[]{"OsmNode", "OsmCollectionEntity"}) {
				database.execSQL("ALTER TABLE " + table + " ADD COLUMN name TEXT");
				database.execSQL("UPDATE " + table + " SET name = " + NAME_IN_JSON_TEXT);
				readEscapedNames(database, table);
				database.execSQL("CREATE INDEX IF NOT EXISTS index_" + table + "_name ON "
						+ table + " (name)");
			}
		}
	};

	/**
	 * Reads again, from the stored element, the names taken out of its text with a backslash in
	 * them: those hold a character JSON writes with one, as a slash or a quote, and a quote
	 * also cut the name short.
	 */
	private static void readEscapedNames(SupportSQLiteDatabase database, String table) {
		List<Object[]> names = new ArrayList<>();
		try (Cursor cursor = database.query("SELECT osmType, osmID, jsonNodeInfo FROM " + table
				+ " WHERE INSTR(name, char(92)) > 0")) {
			while (cursor.moveToNext()) {
				try {
					JSONObject tags = new JSONObject(cursor.getString(2))
							.optJSONObject(ClimbingTags.KEY_TAGS);
					names.add(new Object[]{
							tags == null ? "" : tags.optString(ClimbingTags.KEY_NAME, ""),
							cursor.getString(0), cursor.getLong(1)});
				} catch (JSONException e) {
					// Left as its text has it.
				}
			}
		}
		for (Object[] name : names) {
			database.execSQL("UPDATE " + table + " SET name = ? WHERE osmType = ? AND osmID = ?",
					name);
		}
	}

	private static AppDatabase appDB;
	public static AppDatabase getInstance(AppCompatActivity parent) {
		return getInstance(parent.getApplicationContext());
	}

	protected AppDatabase() {
		//protect teh constructor
	}

	public static synchronized AppDatabase getInstance(Context parent) {
		if (appDB == null) {
			appDB = Room.databaseBuilder(parent,
					AppDatabase.class, OSM_CACHE_DB)
					// Every version from 4 on migrates, so that the downloads and the edits stay;
					// a version without a migration fails to open rather than drop them. The
					// versions before were dropped, all tables, so ones gone from the schema
					// (GeoNode in version 2) too: version 3 dropped what was downloaded before
					// only climbing features were kept, see ClimbingFeature, and version 4 the
					// bounding boxes that took crags across Greenwich to cross the antimeridian
					// and the other way round, see MapBounds.around.
					.addMigrations(MIGRATION_4_5, MIGRATION_5_6)
					.fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
					.build();
		}

		return appDB;
	}

	public abstract OsmNodeDao osmNodeDao();
	public abstract OsmCollectionEntityDao osmCollectionDao();
	public abstract EntityCountryDao entityCountryDao();
	public abstract DownloadedCountryDao downloadedCountryDao();
}
