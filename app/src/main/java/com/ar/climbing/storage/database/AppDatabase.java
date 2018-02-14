package com.ar.climbing.storage.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

/**
 * Created by xyz on 2/8/18.
 */

@Database(entities = {GeoNode.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase{
    public abstract GeoNodeDao nodeDao();
}
