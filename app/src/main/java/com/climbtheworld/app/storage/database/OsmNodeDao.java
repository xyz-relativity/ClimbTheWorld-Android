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

	/**
	 * The nodes of the IDs. With the type, they are looked up through the primary key, which
	 * starts with it: on the IDs alone, SQLite reads the whole table for each query.
	 */
	String NODES_BY_ID = "SELECT * FROM OsmNode WHERE osmType = 'node' AND osmID IN (:ids)";

	@Query(NODES_BY_ID)
	List<OsmNode> resolveNodeData(List<Long> ids);

	/**
	 * The nodes of the types whose name holds the text, by name. Through the name index, which
	 * SQLite does not pick on its own: it is walked in the order of the names, only the rows
	 * whose name matches are read, and it stops at the limit.
	 */
	String NODES_BY_NAME = "SELECT * FROM OsmNode INDEXED BY index_OsmNode_name "
			+ "WHERE localUpdateState != 'toDelete' AND entityClimbingType IN (:types) "
			+ "AND name LIKE '%' || :searchString || '%' ORDER BY name LIMIT :limit";

	@Query(NODES_BY_NAME)
	List<OsmNode> find(String searchString, int limit, OsmEntity.EntityClimbingType... types);

	/**
	 * Changed in the app and waiting for upload: any state but clean, as the two ranges on either
	 * side of it, which SQLite seeks in the state index (see OsmNode). A != cannot seek an index,
	 * so it read the whole table, and a list of the other states would miss one added later.
	 * Like the !=, it leaves out a row without a state.
	 */
	String DIRTY = "(localUpdateState < 'clean' OR localUpdateState > 'clean')";
	String LOAD_DIRTY = "SELECT * FROM OsmNode WHERE " + DIRTY;
	String COUNT_DIRTY = "SELECT COUNT(*) FROM OsmNode WHERE " + DIRTY;

	@Query(LOAD_DIRTY)
	List<OsmNode> loadDirty();

	@Query(COUNT_DIRTY)
	int countDirty();

	/**
	 * The smallest ID, 0 without any node. With the type, it is the first entry of the primary
	 * key, which starts with the type: on the IDs alone, SQLite reads all of it.
	 */
	String MIN_ID = "SELECT COALESCE(MIN(osmID), 0) FROM OsmNode WHERE osmType = 'node'";

	@Query(MIN_ID)
	long minId();

	@Query("DELETE FROM OsmNode WHERE osmType = :osmType AND osmID = :osmID")
	void delete(OsmEntity.EntityOsmType osmType, long osmID);

	/**
	 * The clean nodes no downloaded country holds any more. The + keeps SQLite off the state
	 * index: without statistics, it takes the equality for a narrow one and would walk the index
	 * over nearly every row, looking each up in the table, where reading the table is cheaper.
	 */
	String DELETE_UNOWNED_CLEAN = "DELETE FROM OsmNode WHERE +localUpdateState = 'clean' "
			+ "AND NOT EXISTS (SELECT 1 FROM EntityCountry "
			+ "WHERE EntityCountry.osmType = OsmNode.osmType "
			+ "AND EntityCountry.osmID = OsmNode.osmID)";

	@Query(DELETE_UNOWNED_CLEAN)
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
