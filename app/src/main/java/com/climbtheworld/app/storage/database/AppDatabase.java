package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.Arrays;
import java.util.List;

/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {GeoNode.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {
	public static List<String> hardDatabaseRestVersion = Arrays.asList("2021.03"); //used for hard database reset

	public abstract GeoNodeDao nodeDao();

	public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			database.execSQL("ALTER TABLE `GeoNode` ADD `nodeType` TEXT DEFAULT `" + GeoNode.NodeTypes.route.name() + "`");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLatitude` ON `GeoNode` (`decimalLatitude`)");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLongitude` ON `GeoNode` (`decimalLongitude`)");
		}
	};
}
