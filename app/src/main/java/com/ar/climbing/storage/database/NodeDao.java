package com.ar.climbing.storage.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

/**
 * Created by xyz on 2/8/18.
 */

@Dao
public interface NodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void insertNodes(Node ... nodes);

    @Update
    public void updateNodes(Node ... nodes);

    @Delete
    public void deleteNodes(Node ... nodes);

    @Query("SELECT * FROM node")
    public Node[] loadAllNodes();

    @Query("SELECT * FROM node WHERE " +
            "(degLat BETWEEN :latSouth AND :latNorth) " +
            "AND " +
            "((degLon BETWEEN -180 AND :longEast) OR (degLon BETWEEN :longWest AND 180))")
    public Node[] loadBBox(double latSouth, double longWest, double latNorth, double longEast);

    @Query("SELECT * FROM node WHERE osmID == :nodeID")
    public Node loadNode(long nodeID);

    @Query("SELECT DISTINCT countryISO FROM node WHERE countryISO != null")
    public String[] loadNodeCountries();
}