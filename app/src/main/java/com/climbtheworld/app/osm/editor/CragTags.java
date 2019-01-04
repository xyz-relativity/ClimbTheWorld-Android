package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.ViewGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

public class CragTags extends Tags implements ITags {
    public CragTags(GeoNode poi, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_crag);

        loadStyles(poi);
    }

    @Override
    public void SaveToNode(GeoNode poi) {

    }
}
