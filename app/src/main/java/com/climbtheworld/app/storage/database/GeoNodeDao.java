package com.climbtheworld.app.storage.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

/**
 * Created by xyz on 2/8/18.
 */

@Dao
public interface GeoNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void insertNodesWithReplace(GeoNode... nodes);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public void insertNodesWithIgnore(GeoNode... nodes);

    @Update
    public void updateNodes(GeoNode... nodes);

    @Delete
    public void deleteNodes(GeoNode... nodes);

    //TO_DELETE_STATE = 1
    @Query("SELECT * FROM GeoNode WHERE localUpdateState != 1")
    public List<GeoNode> loadAllNonDeletedNodes();

    //CLEAN_STATE = 0
    @Query("SELECT * FROM GeoNode WHERE localUpdateState != 0")
    public List<GeoNode> loadAllUpdatedNodes();

    @Query("SELECT * FROM GeoNode")
    public List<GeoNode> loadAllNodes();

    //TO_DELETE_STATE = 1
    @Query("SELECT * FROM GeoNode WHERE (localUpdateState != 1)" +
            "AND" +
            "(decimalLatitude BETWEEN :latSouth AND :latNorth) " +
            "AND " +
            "((decimalLongitude BETWEEN -180 AND :longEast) OR (decimalLongitude BETWEEN :longWest AND 180))")
    public List<GeoNode> loadBBox(double latNorth, double longEast, double latSouth, double longWest);

    @Query("SELECT * FROM GeoNode WHERE osmID == :nodeID")
    public GeoNode loadNode(long nodeID);

    @Query("SELECT * FROM GeoNode WHERE countryIso == :countryIsoName COLLATE NOCASE")
    public List<GeoNode> loadNodesFromCountry(String countryIsoName);

    @Query("SELECT DISTINCT countryIso FROM GeoNode")
    public List<String> loadCountriesIso();

    @Query("SELECT osmID FROM GeoNode ORDER BY osmID ASC LIMIT 1")
    public long getSmallestId();
}