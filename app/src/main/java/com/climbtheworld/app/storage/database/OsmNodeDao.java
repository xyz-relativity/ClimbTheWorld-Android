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
public interface OsmNodeDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertNodesWithReplace(List<OsmNode> nodes);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertNodesWithIgnore(List<OsmNode> nodes);

	@Query("SELECT * FROM OsmNode WHERE osmID IN (:ids)")
	List<OsmNode> resolveNodeData(List<Long> ids);

	//TO_DELETE_STATE = 1
	@Query("SELECT osmID FROM OsmNode WHERE (localUpdateState != 1)" +
			"AND " +
			"(entityClimbingType IN (:type))" +
			"AND" +
			"(decimalLatitude BETWEEN :latSouth AND :latNorth) " +
			"AND " +
			"(decimalLongitude BETWEEN :longWest AND :longEast)")
	List<Long> loadBBox(double latNorth, double longEast, double latSouth, double longWest, @Nullable OsmEntity.EntityClimbingType ... type);
}
