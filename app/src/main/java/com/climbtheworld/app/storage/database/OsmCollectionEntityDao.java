package com.climbtheworld.app.storage.database;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RoomWarnings;
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

	// Ways (shown on the map as a single marker, e.g. a gym mapped as a building) and crag/area
	// relations (shown as a labelled outline).
	@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
	@Query("SELECT *, SUBSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), 0, " +
			"INSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), '\"')) name " +
			"FROM OsmCollectionEntity WHERE localUpdateState != 'toDelete' " +
			"AND ((osmType = 'way' AND entityClimbingType IN ('route', 'crag', 'area', 'artificial')) " +
			"OR (osmType = 'relation' AND entityClimbingType IN ('crag', 'area'))) " +
			// An all-zero bbox means none of the members were resolved, so there's no location.
			"AND NOT (bBoxNorth = 0 AND bBoxSouth = 0 AND bBoxEast = 0 AND bBoxWest = 0) " +
			"AND name LIKE '%' || :searchString || '%' COLLATE NOCASE ORDER BY name LIMIT :limit")
	List<OsmCollectionEntity> findCollections(String searchString, int limit);

	@Query("SELECT * FROM OsmCollectionEntity WHERE localUpdateState != 'clean'")
	List<OsmCollectionEntity> loadDirty();

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
