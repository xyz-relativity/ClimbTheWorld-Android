package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.ViewGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

public class ArtificialTags extends Tags implements ITags {
    public ArtificialTags(GeoNode editNode, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_artificial);
    }

    @Override
    public void SaveToNode(GeoNode editNode) {

    }

    @Override
    public void CancelNode(GeoNode editNode) {

    }
}
