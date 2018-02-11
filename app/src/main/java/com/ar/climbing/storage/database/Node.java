package com.ar.climbing.storage.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by xyz on 2/8/18.
 */

@Entity
public class Node {
    public static final int CLEAN_STATE = 0;
    public static final int TO_DELETE_STATE = 1;
    public static final int TO_UPDATE_STATE = 2;

    @PrimaryKey
    public long osmID;

    public double degLat;
    public double degLon;
    public double metersElev;
    public String countryISO;
    public long updateDate;
    public int updateStatus;
    public String nodeInfo;
}
