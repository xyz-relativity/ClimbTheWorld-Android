package com.climbtheworld.app.storage.database;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {GeoNode.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {
	public abstract GeoNodeDao nodeDao();

	public static final Migration MIGRATION_0_1 = new Migration(0, 1) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLatitude` ON `GeoNode` (`decimalLatitude`)");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLongitude` ON `GeoNode` (`decimalLongitude`)");
		}
	};

	public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			database.execSQL("ALTER TABLE `GeoNode` ADD `nodeType` TEXT DEFAULT `" + GeoNode.NodeTypes.route.name() + "`");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLatitude` ON `GeoNode` (`decimalLatitude`)");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLongitude` ON `GeoNode` (`decimalLongitude`)");
		}
	};

	public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
		@Override
		public void migrate(@NonNull SupportSQLiteDatabase database) {
			database.execSQL("ALTER TABLE `GeoNode` RENAME COLUMN `nodeType` to `type`");

			//change type default value
			SupportSQLiteStatement statement = database.compileStatement("UPDATE `GeoNode` SET `type` = ? WHERE `osmID` = ?");
			Cursor cursor = database.query("SELECT * FROM `GeoNode`");
			int id = cursor.getColumnIndexOrThrow("osmID");
			while(cursor.moveToNext()) {
				statement.bindString(1, GeoNode.Type.node.name());
				statement.bindLong(2, cursor.getLong(id));
				statement.execute();
			}

			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLatitude` ON `GeoNode` (`decimalLatitude`)");
			database.execSQL("CREATE INDEX IF NOT EXISTS `index_GeoNode_decimalLongitude` ON `GeoNode` (`decimalLongitude`)");
		}
	};
}
