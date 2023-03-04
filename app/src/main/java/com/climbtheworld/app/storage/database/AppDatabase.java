package com.climbtheworld.app.storage.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.Arrays;
import java.util.List;

/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {GeoNode.class, OsmNode.class, OsmWay.class, OsmRelation.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
	private static final String OSM_CACHE_DB = "overpassCache.db";
	private static final List<String> hardDatabaseRestVersion = Arrays.asList("2023.02-dev"); //used for hard database reset

	private static AppDatabase appDB;
	public static AppDatabase getInstance(AppCompatActivity parent) {
		return getInstance(parent.getApplicationContext());
	}

	protected AppDatabase() {
		//protect teh constructor
	}

	public static synchronized AppDatabase getInstance(Context parent) {
		if (appDB == null) {
			Configs configs = Configs.instance(parent);
			if (!Globals.versionName.equalsIgnoreCase(configs.getString(Configs.ConfigKey.installedVersion)) && AppDatabase.hardDatabaseRestVersion.contains(Globals.versionName)) {
				configs.setString(Configs.ConfigKey.installedVersion, Globals.versionName);
				String[] dbList = parent.databaseList();
				for (String delDB: dbList) {
					parent.deleteDatabase(delDB);
				}
			}

			appDB = Room.databaseBuilder(parent,
					AppDatabase.class, OSM_CACHE_DB)
					.addMigrations(AppDatabase.MIGRATION_1_2)
					.fallbackToDestructiveMigration()
					.build();
		}

		return appDB;
	}

	public abstract GeoNodeDao nodeDao();
	public abstract OsmNodeDao osmNodeDao();
	public abstract OsmWayDao osmWayDao();
	public abstract OsmRelationDao osmRelationDao();

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

	public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			database.execSQL("ALTER TABLE `GeoNode` ADD `nodeType` TEXT DEFAULT `" + GeoNode.NodeTypes.route.name() + "`");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLatitude` ON `GeoNode` (`decimalLatitude`)");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLongitude` ON `GeoNode` (`decimalLongitude`)");
		}
	};
}
