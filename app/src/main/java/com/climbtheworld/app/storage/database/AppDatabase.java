package com.climbtheworld.app.storage.database;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.migration.Migration;
import android.support.annotation.NonNull;

/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {GeoNode.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase{
    public abstract GeoNodeDao nodeDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `GeoNode` ADD `nodeType` TEXT DEFAULT `" + GeoNode.NodeTypes.route.name() + "`");
            database.execSQL("CREATE  INDEX `index_GeoNode_decimalLatitude` ON `GeoNode` (`decimalLatitude`)");
            database.execSQL("CREATE  INDEX `index_GeoNode_decimalLongitude` ON `GeoNode` (`decimalLongitude`)");
        }
    };
}
