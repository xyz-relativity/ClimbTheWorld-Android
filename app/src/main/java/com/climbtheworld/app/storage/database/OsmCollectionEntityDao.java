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

	@Query("DELETE FROM OsmCollectionEntity WHERE localUpdateState = 'clean' AND NOT EXISTS (" +
			"SELECT 1 FROM EntityCountry WHERE EntityCountry.osmType = OsmCollectionEntity.osmType " +
			"AND EntityCountry.osmID = OsmCollectionEntity.osmID)")
	void deleteUnownedCleanCollections();

	@Query("SELECT COUNT(*) FROM OsmCollectionEntity")
	int count();

	@Query("SELECT * FROM OsmCollectionEntity WHERE osmType = :osmType AND osmID = :osmID")
	OsmCollectionEntity find(OsmEntity.EntityOsmType osmType, long osmID);

	@Query("SELECT osmID FROM OsmCollectionEntity WHERE (localUpdateState != 1)" +
			"AND " +
			"(entityClimbingType IN (:type))" +
			"AND NOT" +
			"(bBoxWest >= :longEast OR bBoxEast <= :longWest OR bBoxSouth >= :latNorth OR bBoxNorth <= :latSouth)") //test bbox
	List<Long> loadBBox(double latNorth, double longEast, double latSouth, double longWest, @Nullable OsmEntity.EntityClimbingType ... type);
}
