package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

public class ArtificialTags extends Tags implements ITags {
	private final RadioGroup venueType;

	public ArtificialTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_artificial);

		this.venueType = container.findViewById(R.id.radioGroupType);
		if (editNode.isArtificialTower()) {
			venueType.check(R.id.radioTower);
		} else {
			venueType.check(R.id.radioGym);
		}
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		switch (venueType.getCheckedRadioButtonId()) {
			case R.id.radioGym:
				editNode.setKey(ClimbingTags.KEY_MAN_MADE, null);
				editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, null);
				break;
			case R.id.radioTower:
				editNode.setKey(ClimbingTags.KEY_MAN_MADE, "tower");
				editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, "climbing");
				break;
		}

		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_MAN_MADE, null);
		editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, null);
	}
}
