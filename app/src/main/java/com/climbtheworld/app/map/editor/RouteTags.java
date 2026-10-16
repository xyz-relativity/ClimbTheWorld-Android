package com.climbtheworld.app.map.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.SpinnerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RouteTags extends Tags implements ITags {

	private final EditText editLength;
	private final EditText editPitches;
	private final EditText editBolts;
	private Spinner dropdownGrade;
	private final TextView labelGrade;
	/**
	 * The grade tags as loaded. Each save starts from them, so that the grading systems picked on
	 * the way to the one kept leave no tags behind.
	 */
	private final Map<String, String> loadedGrades;
	/** The system the grade is picked in and saved in, as climbing:grade:&lt;system&gt;. */
	private GradeSystem gradeSystem;

	public RouteTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_route);

		this.editLength = container.findViewById(R.id.editLength);
		this.editPitches = container.findViewById(R.id.editpitches);
		this.editBolts = container.findViewById(R.id.editbolts);
		this.dropdownGrade = container.findViewById(R.id.gradeSelectSpinner);
		this.labelGrade = container.findViewById(R.id.routeGrading);
		Spinner dropdownGradeSystem = container.findViewById(R.id.gradeSystemSpinner);

		loadedGrades = editNode.getLevelTags(ClimbingTags.KEY_GRADE_TAG);
		// The system the grade is tagged in, or the one the user reads grades in for a new grade.
		gradeSystem = editNode.getLevelSystem(ClimbingTags.KEY_GRADE_TAG, SpinnerUtils.getGradeSystem(parent));

		final GradeSystem[] gradeSystems = GradeSystem.printableValues();
		dropdownGradeSystem.setAdapter(new GradeSystem.GradeSystemArrayAdapter(parent,
				android.R.layout.simple_spinner_dropdown_item, gradeSystems));
		dropdownGradeSystem.setSelection(Arrays.asList(gradeSystems).indexOf(gradeSystem), false);
		showGrades(editNode);

		dropdownGradeSystem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				// The spinner also reports the selection it starts with.
				if (gradeSystems[i] == gradeSystem) {
					return;
				}
				gradeSystem = gradeSystems[i];
				// The grade picked so far, as the new system has it.
				showGrades(editNode);
				saveToNode(editNode);
				refreshEditor();
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});

		dropdownGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				saveToNode(editNode);
				refreshEditor();
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {
				saveToNode(editNode);
				refreshEditor();
			}
		});

		loadStyles(editNode);

		editLength.setText(editNode.getKey(ClimbingTags.KEY_LENGTH));
		editPitches.setText(editNode.getKey(ClimbingTags.KEY_PITCHES));
		editBolts.setText(editNode.getKey(ClimbingTags.KEY_BOLTS));
	}

	/**
	 * Names gradeSystem in the header of the grade and lists its grades, with the node's grade in
	 * it selected.
	 */
	private void showGrades(GeoNode node) {
		labelGrade.setText(parent.getResources().getString(R.string.grade_system,
				parent.getResources().getString(gradeSystem.shortName)));
		SpinnerUtils.updateGradeSpinner(parent, dropdownGrade, gradeSystem,
				node.getLevelId(ClimbingTags.KEY_GRADE_TAG, gradeSystem), true);
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_LENGTH, editLength.getText().toString());
		editNode.setKey(ClimbingTags.KEY_PITCHES, editPitches.getText().toString());
		editNode.setKey(ClimbingTags.KEY_BOLTS, editBolts.getText().toString());

		saveStyles(editNode);
		editNode.setLevelTags(ClimbingTags.KEY_GRADE_TAG, loadedGrades);
		editNode.setLevelFromID(SpinnerUtils.getGradeID(dropdownGrade, gradeSystem, true),
				ClimbingTags.KEY_GRADE_TAG, gradeSystem);
		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {
		editNode.setKey(ClimbingTags.KEY_LENGTH, null);
		editNode.setKey(ClimbingTags.KEY_PITCHES, null);
		editNode.setKey(ClimbingTags.KEY_BOLTS, null);
		editNode.removeLevelTags(ClimbingTags.KEY_GRADE_TAG);

		List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
		editNode.setClimbingStyles(styles);
	}
}
