package com.climbtheworld.app.osm.editor;

import com.climbtheworld.app.storage.database.GeoNode;

public interface ITags {
    void SaveToNode(GeoNode editNode);
    void showTags();
    void hideTags();
}