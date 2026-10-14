package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

/**
 * The kind of artificial climbing, tagged as the OSM wiki's Climbing page has it: a gym is a
 * leisure=sports_centre (or a sports_hall, which stays one), a simple wall a leisure=pitch, and a
 * tower a leisure=pitch with man_made=tower and tower:type=climbing.
 */
public class ArtificialTags extends Tags implements ITags {
	private final RadioGroup venueType;
	/** The leisure value the gym choice writes. */
	private final String gymLeisure;

	public ArtificialTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_artificial);

		String leisure = editNode.getKey(ClimbingTags.KEY_LEISURE);
		this.gymLeisure = ClimbingTags.VALUE_SPORTS_HALL.equals(leisure)
				? ClimbingTags.VALUE_SPORTS_HALL : ClimbingTags.VALUE_SPORTS_CENTRE;

		this.venueType = container.findViewById(R.id.radioGroupType);
		if (editNode.isArtificialTower()) {
			venueType.check(R.id.radioTower);
		} else if (editNode.isClimbingWall()) {
			venueType.check(R.id.radioWall);
		} else {
			venueType.check(R.id.radioGym);
		}
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		int id = venueType.getCheckedRadioButtonId();
		if (id == R.id.radioGym) {
			editNode.setKey(ClimbingTags.KEY_LEISURE, gymLeisure);
			editNode.setKey(ClimbingTags.KEY_MAN_MADE, null);
			editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, null);
		} else if (id == R.id.radioWall) {
			editNode.setKey(ClimbingTags.KEY_LEISURE, ClimbingTags.VALUE_PITCH);
			editNode.setKey(ClimbingTags.KEY_MAN_MADE, null);
			editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, null);
		} else if (id == R.id.radioTower) {
			editNode.setKey(ClimbingTags.KEY_LEISURE, ClimbingTags.VALUE_PITCH);
			editNode.setKey(ClimbingTags.KEY_MAN_MADE, ClimbingTags.KEY_TOWER);
			editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, ClimbingTags.VALUE_CLIMBING);
		}

		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_MAN_MADE, null);
		editNode.setKey(ClimbingTags.KEY_TOWER_TYPE, null);
	}
}
