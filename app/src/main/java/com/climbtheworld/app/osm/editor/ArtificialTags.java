package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

public class ArtificialTags extends Tags implements ITags {
    private final RadioGroup venueType;
    public ArtificialTags(GeoNode editNode, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_artificial);

        this.venueType = container.findViewById(R.id.radioGroupType);
        if (editNode.isArtificialTower()) {
            venueType.check(R.id.radioTower);
        } else {
            venueType.check(R.id.radioGym);
        }
    }

    @Override
    public void saveToNode(GeoNode editNode) {
        switch (venueType.getCheckedRadioButtonId()) {
            case R.id.radioGym:
                editNode.setKey(GeoNode.KEY_MAN_MADE, null);
                editNode.setKey(GeoNode.KEY_TOWER_TYPE, null);
                break;
            case R.id.radioTower:
                editNode.setKey(GeoNode.KEY_MAN_MADE, "tower");
                editNode.setKey(GeoNode.KEY_TOWER_TYPE, "climbing");
                break;
        }
    }

    @Override
    public void cancelNode(GeoNode editNode) {
        editNode.setKey(GeoNode.KEY_MAN_MADE, null);
        editNode.setKey(GeoNode.KEY_TOWER_TYPE, null);
    }
}
