package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.TypeConverters;

import java.util.List;

@Dao
@TypeConverters(DataConverter.class)
public interface OsmRelationDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertRelationWithReplace(List<OsmRelation> relations);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertRelationWithIgnore(List<OsmRelation> relations);
}
