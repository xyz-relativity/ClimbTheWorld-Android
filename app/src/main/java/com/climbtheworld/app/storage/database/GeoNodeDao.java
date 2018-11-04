package com.climbtheworld.app.storage.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.TypeConverters;
import android.arch.persistence.room.Update;

import com.climbtheworld.app.tools.DataConverter;

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

    //TO_DELETE_STATE = 1
    @Query("SELECT * FROM GeoNode WHERE localUpdateState != 1 ORDER BY decimalLatitude DESC")
    List<GeoNode> loadAllNonDeletedNodes();

    //CLEAN_STATE = 0
    @Query("SELECT * FROM GeoNode WHERE localUpdateState != 0 ORDER BY decimalLatitude DESC")
    List<GeoNode> loadAllUpdatedNodes();

    @Query("SELECT * FROM GeoNode WHERE nodeType == :type ORDER BY decimalLatitude DESC")
    List<GeoNode> loadAllNodes(GeoNode.NodeTypes type);

    //TO_DELETE_STATE = 1
    @Query("SELECT * FROM GeoNode WHERE (localUpdateState != 1)" +
            "AND" +
            "(decimalLatitude BETWEEN :latSouth AND :latNorth) " +
            "AND " +
            "(decimalLongitude BETWEEN :longWest AND :longEast) AND nodeType == :type ORDER BY decimalLatitude DESC")
    List<GeoNode> loadBBoxByType(double latNorth, double longEast, double latSouth, double longWest, GeoNode.NodeTypes type);

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
}