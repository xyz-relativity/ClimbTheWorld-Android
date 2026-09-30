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
		GeoNode.class,
		OsmNode.class,
		OsmCollectionEntity.class,
		EntityCountry.class,
		DownloadedCountry.class
}, version = 1)
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
					.fallbackToDestructiveMigration()
					.build();
		}

		return appDB;
	}

	public abstract GeoNodeDao nodeDao();
	public abstract OsmNodeDao osmNodeDao();
	public abstract OsmCollectionEntityDao osmCollectionDao();
	public abstract EntityCountryDao entityCountryDao();
	public abstract DownloadedCountryDao downloadedCountryDao();

	public Long getNewNodeID() {
		long tmpID = appDB.nodeDao().getSmallestId();
		//if the smallest ID is positive this is the first node creates, so set the id to -1.
		if (tmpID >= 0) {
			tmpID = -1L;
		} else {
			tmpID -= 1;
		}
		return tmpID;
	}
}
