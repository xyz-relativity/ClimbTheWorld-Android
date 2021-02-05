package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.TypeConverters;
import androidx.room.Update;

import java.util.List;

/**
 * Created by xyz on 2/8/18.
 */

@Dao
@TypeConverters(DataConverter.class)
public interface GeoNodeDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertNodesWithReplace(GeoNode... nodes);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertNodesWithIgnore(GeoNode... nodes);

	@Update
	void updateNodes(GeoNode... nodes);

	@Delete
	void deleteNodes(GeoNode... nodes);

	//CLEAN_STATE = 0
	@Query("SELECT * FROM GeoNode WHERE localUpdateState != 0 ORDER BY decimalLatitude DESC")
	List<GeoNode> loadAllUpdatedNodes();

	//TO_DELETE_STATE = 1
	@Query("SELECT * FROM GeoNode WHERE (localUpdateState != 1)" +
			"AND" +
			"(decimalLatitude BETWEEN :latSouth AND :latNorth) " +
			"AND " +
			"(decimalLongitude BETWEEN :longWest AND :longEast) ORDER BY decimalLatitude DESC")
	List<GeoNode> loadBBox(double latNorth, double longEast, double latSouth, double longWest);

	@Query("SELECT * FROM GeoNode WHERE osmID == :nodeID")
	GeoNode loadNode(long nodeID);

	@Query("SELECT * FROM GeoNode WHERE countryIso == :countryIsoName COLLATE NOCASE")
	List<GeoNode> loadNodesFromCountry(String countryIsoName);

	@Query("DELETE FROM GeoNode WHERE countryIso == :countryIsoName COLLATE NOCASE")
	void deleteNodesFromCountry(String countryIsoName);

	@Query("SELECT DISTINCT countryIso FROM GeoNode")
	List<String> loadCountriesIso();

	@Query("SELECT osmID FROM GeoNode ORDER BY osmID ASC LIMIT 1")
	long getSmallestId();

	//add suppress for the custom "name" field
	@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
	@Query("SELECT *, SUBSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), 0, INSTR(SUBSTR(jsonNodeInfo, INSTR(jsonNodeInfo, '\"name\":\"') + 8), '\"')) name FROM GeoNode WHERE name LIKE '%' || :searchString || '%' COLLATE NOCASE ORDER BY name")
	List<GeoNode> find(String searchString);
}