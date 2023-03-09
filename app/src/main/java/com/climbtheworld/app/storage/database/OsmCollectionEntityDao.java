package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;

import java.util.List;

@Dao
@TypeConverters(DataConverter.class)
public interface OsmCollectionEntityDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertCollectionWithReplace(List<OsmCollectionEntity> relations);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertCollectionWithIgnore(List<OsmCollectionEntity> relations);

	//TO_DELETE_STATE = 1
	@Query("SELECT * FROM OsmCollectionEntity WHERE (localUpdateState != 1)" +
			"AND" +
			"(entityClimbingType == :type)" +
			"AND NOT" +
			"(bBoxWest >= :longEast OR bBoxEast <= :longWest OR bBoxSouth >= :latNorth OR bBoxNorth <= :latSouth)") //test bbox
	List<OsmCollectionEntity> loadBBox(double latNorth, double longEast, double latSouth, double longWest, OsmEntity.EntityClimbingType type);
}
