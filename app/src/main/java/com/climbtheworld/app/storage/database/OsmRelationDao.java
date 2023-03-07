package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;

import java.util.List;

@Dao
@TypeConverters(DataConverter.class)
public interface OsmRelationDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertRelationWithReplace(List<OsmRelation> relations);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertRelationWithIgnore(List<OsmRelation> relations);

	//TO_DELETE_STATE = 1
	@Query("SELECT * FROM OsmRelation WHERE (localUpdateState != 1)" +
			"AND" +
			"(entityType == :type)" +
			"AND NOT" +
			"(bBoxWest >= :longEast OR bBoxEast <= :longWest OR bBoxSouth >= :latNorth OR bBoxNorth <= :latSouth)") //test bbox
	List<OsmRelation> loadBBox(double latNorth, double longEast, double latSouth, double longWest, OsmEntity.EntityType type);
}
