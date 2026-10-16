package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The section of a crag or area node. It has no grade range: crags and areas are mapped as OSM
 * relations, whose grades are those of their routes.
 */
public class CragTags extends Tags implements ITags {
	private final EditText editNumRoutes;
	private final EditText editMinLength;
	private final EditText editMaxLength;

	public CragTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_crag);

		this.editNumRoutes = container.findViewById(R.id.editNumRoutes);
		this.editMinLength = container.findViewById(R.id.editMinLength);
		this.editMaxLength = container.findViewById(R.id.editMaxLength);

		editNumRoutes.setText(editNode.getKey(ClimbingTags.KEY_ROUTES));
		editMinLength.setText(editNode.getKey(ClimbingTags.KEY_MIN_LENGTH));
		editMaxLength.setText(editNode.getKey(ClimbingTags.KEY_MAX_LENGTH));

		loadStyles(editNode);
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_ROUTES, editNumRoutes.getText().toString());
		editNode.setKey(ClimbingTags.KEY_MIN_LENGTH, editMinLength.getText().toString());
		editNode.setKey(ClimbingTags.KEY_MAX_LENGTH, editMaxLength.getText().toString());

		saveStyles(editNode);

		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_ROUTES, null);
		editNode.setKey(ClimbingTags.KEY_MIN_LENGTH, null);
		editNode.setKey(ClimbingTags.KEY_MAX_LENGTH, null);

		// A node that stops being a crag also loses a grade range it was tagged with.
		editNode.removeLevelTags(ClimbingTags.KEY_GRADE_TAG_MIN);
		editNode.removeLevelTags(ClimbingTags.KEY_GRADE_TAG_MAX);

		List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
		editNode.setClimbingStyles(styles);
	}
}
