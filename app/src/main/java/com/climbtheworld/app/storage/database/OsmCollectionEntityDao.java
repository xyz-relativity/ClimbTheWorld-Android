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
	/** The name tag taken out of the stored JSON, as a name column for searching. */
	String NAME_COLUMN = "SUBSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), 0, " +
			"INSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), '\"')) name";
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

	@Query("SELECT * FROM OsmCollectionEntity WHERE osmID IN (:ids)")
	List<OsmCollectionEntity> resolveData(List<Long> ids);

	// Route ways (drawn as a line), gym ways (shown on the map as a single marker, e.g. a gym
	// mapped as a building) and crag/area relations (shown as a labelled outline).
	@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
	@Query("SELECT *, " + NAME_COLUMN + " " +
			"FROM OsmCollectionEntity WHERE localUpdateState != 'toDelete' " +
			"AND ((osmType = 'way' AND entityClimbingType IN ('route', 'artificial')) " +
			"OR (osmType = 'relation' AND entityClimbingType IN ('crag', 'area'))) " +
			// An all-zero bbox means none of the members were resolved, so there's no location.
			"AND NOT (bBoxNorth = 0 AND bBoxSouth = 0 AND bBoxEast = 0 AND bBoxWest = 0) " +
			"AND name LIKE '%' || :searchString || '%' COLLATE NOCASE ORDER BY name LIMIT :limit")
	List<OsmCollectionEntity> findCollections(String searchString, int limit);

	/** Relations of a climbing type whose name holds the text, in CHANGED_THEN_NEAREST order. */
	@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
	@Query("SELECT *, " + NAME_COLUMN + " " +
			"FROM OsmCollectionEntity WHERE osmType = 'relation' AND entityClimbingType = :type " +
			"AND localUpdateState != 'toDelete' " +
			// An all-zero bbox means none of the members were resolved, so there's no location.
			"AND NOT (bBoxNorth = 0 AND bBoxSouth = 0 AND bBoxEast = 0 AND bBoxWest = 0) " +
			"AND name LIKE '%' || :searchString || '%' COLLATE NOCASE " +
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

	@Query("SELECT COALESCE(MIN(osmID), 0) FROM OsmCollectionEntity")
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
