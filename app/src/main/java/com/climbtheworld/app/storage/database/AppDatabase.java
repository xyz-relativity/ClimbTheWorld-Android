package com.climbtheworld.app.storage.database;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;


/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {
		OsmNode.class,
		OsmCollectionEntity.class,
		EntityCountry.class,
		DownloadedCountry.class
}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
	private static final String OSM_CACHE_DB = "offlineClimbingData.db";
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
					// All tables, so ones dropped from the schema (GeoNode in version 2) go too.
					// Version 3 drops what was downloaded before only climbing features were kept,
					// see ClimbingFeature; the countries have to be downloaded again.
					// Version 4 drops the bounding boxes that took crags across Greenwich to cross
					// the antimeridian and the other way round, see MapBounds.around.
					.fallbackToDestructiveMigration(true)
					.build();
		}

		return appDB;
	}

	public abstract OsmNodeDao osmNodeDao();
	public abstract OsmCollectionEntityDao osmCollectionDao();
	public abstract EntityCountryDao entityCountryDao();
	public abstract DownloadedCountryDao downloadedCountryDao();
}
