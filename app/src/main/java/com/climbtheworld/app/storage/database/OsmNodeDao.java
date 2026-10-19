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
public interface OsmNodeDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertNodesWithReplace(List<OsmNode> nodes);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertNodesWithIgnore(List<OsmNode> nodes);

	@Query("SELECT * FROM OsmNode WHERE osmID IN (:ids)")
	List<OsmNode> resolveNodeData(List<Long> ids);

	@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
	@Query("SELECT *, SUBSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), 0, " +
			"INSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), '\"')) name " +
			"FROM OsmNode WHERE localUpdateState != 'toDelete' AND entityClimbingType IN (:types) " +
			"AND name LIKE '%' || :searchString || '%' COLLATE NOCASE ORDER BY name LIMIT :limit")
	List<OsmNode> find(String searchString, int limit, OsmEntity.EntityClimbingType... types);

	@Query("SELECT * FROM OsmNode WHERE localUpdateState != 'clean'")
	List<OsmNode> loadDirty();

	@Query("SELECT COUNT(*) FROM OsmNode WHERE localUpdateState != 'clean'")
	int countDirty();

	@Query("SELECT COALESCE(MIN(osmID), 0) FROM OsmNode")
	long minId();

	@Query("DELETE FROM OsmNode WHERE osmType = :osmType AND osmID = :osmID")
	void delete(OsmEntity.EntityOsmType osmType, long osmID);

	@Query("DELETE FROM OsmNode WHERE localUpdateState = 'clean' AND NOT EXISTS (" +
			"SELECT 1 FROM EntityCountry WHERE EntityCountry.osmType = OsmNode.osmType " +
			"AND EntityCountry.osmID = OsmNode.osmID)")
	void deleteUnownedCleanNodes();

	@Query("SELECT COUNT(*) FROM OsmNode")
	int count();

	@Query("SELECT * FROM OsmNode WHERE osmType = :osmType AND osmID = :osmID")
	OsmNode find(OsmEntity.EntityOsmType osmType, long osmID);

	@Query("SELECT osmID FROM OsmNode WHERE (localUpdateState != 'toDelete')" +
			"AND " +
			"(entityClimbingType IN (:type))" +
			"AND" +
			"(decimalLatitude BETWEEN :latSouth AND :latNorth) " +
			"AND " +
			"(decimalLongitude BETWEEN :longWest AND :longEast)")
	List<Long> loadBBox(double latNorth, double longEast, double latSouth, double longWest, @Nullable OsmEntity.EntityClimbingType ... type);
}
