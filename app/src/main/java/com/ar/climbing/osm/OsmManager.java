package com.ar.climbing.osm;

import com.ar.climbing.storage.DataManager;
import com.ar.climbing.storage.database.GeoNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OsmManager {
    public void pushData(final List<Long> toChange) {
        (new Thread() {
            public void run() {
                DataManager dataMgr = new DataManager();
                Map<Long, GeoNode> poiMap = new HashMap<>();
                dataMgr.downloadIDs(toChange, poiMap);
            }
        }).run();
    }
}
