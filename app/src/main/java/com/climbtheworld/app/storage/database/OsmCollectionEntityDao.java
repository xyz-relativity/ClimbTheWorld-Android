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
	/**
	 * Degrees east from :longitude to the middle of the bounding box, past 180 when the box
	 * crosses the antimeridian east of :longitude.
	 */
	String LONGITUDE_TO_MIDDLE = "((bBoxEast + bBoxWest " +
			"+ CASE WHEN bBoxWest > bBoxEast THEN 360 ELSE 0 END) / 2 - :longitude)";
	/** LONGITUDE_TO_MIDDLE the short way round, from -180 to 180. */
	String SHORT_LONGITUDE_TO_MIDDLE = "(" + LONGITUDE_TO_MIDDLE + " - 360 * ROUND("
			+ LONGITUDE_TO_MIDDLE + " / 360))";
	/**
	 * Changed in the app first, most recently first, then nearest to :latitude and :longitude. The
	 * distance is to the middle of the bounding box in plain degrees, close enough to tell which
	 * ones make a limit.
	 */
	String CHANGED_THEN_NEAREST = "localEditDate DESC, " +
			"((bBoxNorth + bBoxSouth) / 2 - :latitude) * ((bBoxNorth + bBoxSouth) / 2 - :latitude) " +
			"+ " + SHORT_LONGITUDE_TO_MIDDLE + " * " + SHORT_LONGITUDE_TO_MIDDLE;
	/**
	 * The bounding box overlaps the box from :latSouth to :latNorth and :longWest to :longEast.
	 * That box must not cross the antimeridian, but the bounding box may: it then has its west
	 * side east of its east side.
	 */
	String OVERLAPS_BOX = "(bBoxSouth < :latNorth AND bBoxNorth > :latSouth AND (" +
			"(bBoxWest <= bBoxEast AND bBoxWest < :longEast AND bBoxEast > :longWest) " +
			"OR (bBoxWest > bBoxEast AND (bBoxWest < :longEast OR bBoxEast > :longWest))))";

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertCollectionWithReplace(List<OsmCollectionEntity> relations);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertCollectionWithIgnore(List<OsmCollectionEntity> relations);

	/**
	 * The ways and relations of the IDs, both of an ID they share. With the types, they are
	 * looked up through the primary key, which starts with the type: on the IDs alone, SQLite
	 * reads the whole table for each query.
	 */
	String COLLECTIONS_BY_ID = "SELECT * FROM OsmCollectionEntity "
			+ "WHERE osmType IN ('way', 'relation') AND osmID IN (:ids)";

	@Query(COLLECTIONS_BY_ID)
	List<OsmCollectionEntity> resolveData(List<Long> ids);

	/**
	 * Route ways (drawn as a line), gym ways (shown on the map as a single marker, e.g. a gym
	 * mapped as a building) and crag/area relations (shown as a labelled outline) whose name
	 * holds the text, by name. Through the name index, see OsmNodeDao.NODES_BY_NAME.
	 */
	String COLLECTIONS_BY_NAME = "SELECT * FROM OsmCollectionEntity "
			+ "INDEXED BY index_OsmCollectionEntity_name WHERE localUpdateState != 'toDelete' "
			+ "AND ((osmType = 'way' AND entityClimbingType IN ('route', 'artificial')) "
			+ "OR (osmType = 'relation' AND entityClimbingType IN ('crag', 'area'))) "
			// An all-zero bbox means none of the members were resolved, so there's no location.
			+ "AND NOT (bBoxNorth = 0 AND bBoxSouth = 0 AND bBoxEast = 0 AND bBoxWest = 0) "
			+ "AND name LIKE '%' || :searchString || '%' ORDER BY name LIMIT :limit";

	@Query(COLLECTIONS_BY_NAME)
	List<OsmCollectionEntity> findCollections(String searchString, int limit);

	/** Relations of a climbing type whose name holds the text, in CHANGED_THEN_NEAREST order. */
	@Query("SELECT * " +
			"FROM OsmCollectionEntity WHERE osmType = 'relation' AND entityClimbingType = :type " +
			"AND localUpdateState != 'toDelete' " +
			// An all-zero bbox means none of the members were resolved, so there's no location.
			"AND NOT (bBoxNorth = 0 AND bBoxSouth = 0 AND bBoxEast = 0 AND bBoxWest = 0) " +
			"AND name LIKE '%' || :searchString || '%' " +
			"ORDER BY " + CHANGED_THEN_NEAREST + " LIMIT :limit")
	List<OsmCollectionEntity> findRelations(String searchString, OsmEntity.EntityClimbingType type,
	                                        double latitude, double longitude, int limit);

	/**
	 * Relations of a climbing type overlapping the box, which must not cross the antimeridian, in
	 * CHANGED_THEN_NEAREST order.
	 */
	@Query("SELECT * FROM OsmCollectionEntity WHERE osmType = 'relation' " +
			"AND entityClimbingType = :type AND localUpdateState != 'toDelete' " +
			"AND " + OVERLAPS_BOX + " " +
			"ORDER BY " + CHANGED_THEN_NEAREST + " LIMIT :limit")
	List<OsmCollectionEntity> loadRelationsInBBox(double latNorth, double longEast, double latSouth,
	                                              double longWest, OsmEntity.EntityClimbingType type,
	                                              double latitude, double longitude, int limit);

	@Query("SELECT * FROM OsmCollectionEntity WHERE localUpdateState != 'clean'")
	List<OsmCollectionEntity> loadDirty();

	@Query("SELECT COUNT(*) FROM OsmCollectionEntity WHERE localUpdateState != 'clean'")
	int countDirty();

	/**
	 * The smallest ID, 0 without any way or relation: the smaller of the two types' first
	 * entries in the primary key, see OsmNodeDao.MIN_ID.
	 */
	String MIN_ID = "SELECT COALESCE(MIN(smallest), 0) FROM ("
			+ "SELECT MIN(osmID) AS smallest FROM OsmCollectionEntity WHERE osmType = 'way' "
			+ "UNION ALL "
			+ "SELECT MIN(osmID) FROM OsmCollectionEntity WHERE osmType = 'relation')";

	@Query(MIN_ID)
	long minId();

	@Query("DELETE FROM OsmCollectionEntity WHERE osmType = :osmType AND osmID = :osmID")
	void delete(OsmEntity.EntityOsmType osmType, long osmID);

	/**
	 * Relations with a member of this id. The id column holds " id id ", so the match is exact,
	 * but it ignores the member's type, which the caller still has to check.
	 */
	@Query("SELECT * FROM OsmCollectionEntity WHERE osmType = 'relation' "
			+ "AND osmMembers LIKE '% ' || :memberId || ' %'")
	List<OsmCollectionEntity> findRelationsWithMemberId(long memberId);

	/** Ways and relations whose nodes, nested members' included, contain the node. */
	@Query("SELECT * FROM OsmCollectionEntity WHERE osmNodes LIKE '% ' || :nodeId || ' %'")
	List<OsmCollectionEntity> findContainingNode(long nodeId);

	@Query("DELETE FROM OsmCollectionEntity WHERE localUpdateState = 'clean' AND NOT EXISTS (" +
			"SELECT 1 FROM EntityCountry WHERE EntityCountry.osmType = OsmCollectionEntity.osmType " +
			"AND EntityCountry.osmID = OsmCollectionEntity.osmID)")
	void deleteUnownedCleanCollections();

	@Query("SELECT COUNT(*) FROM OsmCollectionEntity")
	int count();

	@Query("SELECT * FROM OsmCollectionEntity WHERE osmType = :osmType AND osmID = :osmID")
	OsmCollectionEntity find(OsmEntity.EntityOsmType osmType, long osmID);

	@Query("SELECT osmID FROM OsmCollectionEntity WHERE (localUpdateState != 'toDelete')" +
			"AND " +
			"(entityClimbingType IN (:type))" +
			"AND " + OVERLAPS_BOX)
	List<Long> loadBBox(double latNorth, double longEast, double latSouth, double longWest, @Nullable OsmEntity.EntityClimbingType ... type);
}
