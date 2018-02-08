package com.ar.climbing.storage.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by xyz on 2/8/18.
 */

@Entity
public class Node {
    @PrimaryKey
    public long osmID;

    public double degLat;
    public double degLon;
    public double metersElev;
    public String nodeInfo;
}
