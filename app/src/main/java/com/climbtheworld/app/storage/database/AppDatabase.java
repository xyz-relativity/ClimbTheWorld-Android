package com.climbtheworld.app.storage.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;


/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {
		OsmNode.class,
		OsmCollectionEntity.class,
		EntityCountry.class,
		DownloadedCountry.class
}, version = 5, exportSchema = false)
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
					.addMigrations(MIGRATION_4_5)
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
