package com.climbtheworld.app.openstreetmap.editor;

import com.climbtheworld.app.storage.database.GeoNode;

public interface ITags {
    void saveToNode(GeoNode editNode);
    void cancelNode(GeoNode editNode);
    void showTags();
    void hideTags();
}
