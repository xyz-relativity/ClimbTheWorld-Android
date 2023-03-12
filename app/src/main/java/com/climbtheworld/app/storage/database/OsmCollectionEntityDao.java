package com.climbtheworld.app.storage.database;

import androidx.annotation.Nullable;
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

	@Query("SELECT * FROM OsmCollectionEntity WHERE osmID IN (:ids)")
	List<OsmCollectionEntity> resolveData(List<Long> ids);

	//TO_DELETE_STATE = 1
	@Query("SELECT osmID FROM OsmCollectionEntity WHERE (localUpdateState != 1)" +
			"AND " +
			"(:type IS NULL OR entityClimbingType IN (:type))" +
			"AND NOT" +
			"(bBoxWest >= :longEast OR bBoxEast <= :longWest OR bBoxSouth >= :latNorth OR bBoxNorth <= :latSouth)") //test bbox
	List<Long> loadBBox(double latNorth, double longEast, double latSouth, double longWest, @Nullable OsmEntity.EntityClimbingType ... type);
}
