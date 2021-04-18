package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.SpinnerUtils;

import java.util.ArrayList;
import java.util.List;

public class CragTags extends Tags implements ITags {
	private final Spinner minGrade;
	private final Spinner maxGrade;
	private final EditText editNumRoutes;
	private final EditText editMinLength;
	private final EditText editMaxLength;

	public CragTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_crag);

		this.minGrade = container.findViewById(R.id.minGradeSpinner);
		this.maxGrade = container.findViewById(R.id.maxGradeSpinner);
		this.editNumRoutes = container.findViewById(R.id.editNumRoutes);
		this.editMinLength = container.findViewById(R.id.editMinLength);
		this.editMaxLength = container.findViewById(R.id.editMaxLength);

		Configs configs = Configs.instance(parent);

		((TextView) container.findViewById(R.id.minGrading)).setText(
				parent.getResources().getString(R.string.min_grade,
						parent.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) container.findViewById(R.id.maxGrading)).setText(parent.getResources()
				.getString(R.string.max_grade, parent.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));

		SpinnerUtils.updateLinkedGradeSpinners(parent, minGrade, editNode.getLevelId(ClimbingTags.KEY_GRADE_TAG_MIN), maxGrade, editNode.getLevelId(ClimbingTags.KEY_GRADE_TAG_MAX), true, true);

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

		editNode.setLevelFromID(SpinnerUtils.getGradeID(minGrade, true), ClimbingTags.KEY_GRADE_TAG_MIN);
		editNode.setLevelFromID(SpinnerUtils.getGradeID(maxGrade, true), ClimbingTags.KEY_GRADE_TAG_MAX);

		saveStyles(editNode);

		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_ROUTES, null);
		editNode.setKey(ClimbingTags.KEY_MIN_LENGTH, null);
		editNode.setKey(ClimbingTags.KEY_MAX_LENGTH, null);

		editNode.removeLevelTags(ClimbingTags.KEY_GRADE_TAG_MIN);
		editNode.removeLevelTags(ClimbingTags.KEY_GRADE_TAG_MAX);

		List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
		editNode.setClimbingStyles(styles);
	}
}
