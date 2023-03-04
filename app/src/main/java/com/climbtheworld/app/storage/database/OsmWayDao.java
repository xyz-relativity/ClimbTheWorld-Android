package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.TypeConverters;

import java.util.List;

@Dao
@TypeConverters(DataConverter.class)
public interface OsmWayDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertWayWithReplace(List<OsmWay> ways);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertWayWithIgnore(List<OsmWay> ways);
}
