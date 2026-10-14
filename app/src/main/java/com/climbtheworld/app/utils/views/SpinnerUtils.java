package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

public class SpinnerUtils {
	private static final int NO_UNKNOWN_INDEX_OFFSET = 0;
	private static final int WITH_UNKNOWN_INDEX_OFFSET = 1;

	private SpinnerUtils() {
		//hide
	}

	public static GradeSystem getGradeSystem(Context context) {
		return GradeSystem.fromString(Configs.instance(context).getString(Configs.ConfigKey.usedGradeSystem));
	}

	public static void updateGradeSpinner(AppCompatActivity parent, Spinner dropdownGrade, GeoNode node, boolean addUnknown) {
		int idOffset = NO_UNKNOWN_INDEX_OFFSET;
		GradeSystem system = getGradeSystem(parent);

		List<String> allGrades = new ArrayList<String>();
		if (addUnknown) {
			allGrades.add(ClimbingTags.UNKNOWN_GRADE_STRING);
			idOffset = WITH_UNKNOWN_INDEX_OFFSET;
		}
		allGrades.addAll(system.getAllGrades());
		final int gradeOffset = idOffset;
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
			// Change color item
			@Override
			public View getDropDownView(int position, View convertView,
			                            @NonNull ViewGroup itemParent) {
				View mView = super.getDropDownView(position, convertView, itemParent);
				TextView mTextView = (TextView) mView;

				GradeViewUtils.styleGradeLabel(mTextView,
						Globals.gradeToColorState(system.indexAt(position - gradeOffset)).getDefaultColor());
				return mView;
			}
		};

		dropdownGrade.setAdapter(adapter);
		dropdownGrade.setSelection(system.positionOf(node.getLevelId(ClimbingTags.KEY_GRADE_TAG)) + idOffset, false);
	}

	public static void updateLinkedGradeSpinners(AppCompatActivity parent, final Spinner minSpinner, int minSel, final Spinner maxSpinner, int maxSel, final boolean addUnknown) {
		int idOffset = NO_UNKNOWN_INDEX_OFFSET;
		GradeSystem system = getGradeSystem(parent);

		List<String> allGrades = new ArrayList<String>();
		if (addUnknown) {
			allGrades.add(ClimbingTags.UNKNOWN_GRADE_STRING);
			idOffset = WITH_UNKNOWN_INDEX_OFFSET;
		}
		allGrades.addAll(system.getAllGrades());
		final int gradeOffset = idOffset;

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
			// Disable click item < month current
			@Override
			public boolean isEnabled(int position) {
				if (addUnknown && position == 0) {
					return true;
				}
				return (maxSpinner.getSelectedItemPosition() == 0)
						|| (position <= maxSpinner.getSelectedItemPosition());
			}

			// Change color item
			@Override
			public View getDropDownView(int position, View convertView,
			                            @NonNull ViewGroup itemParent) {
				View mView = super.getDropDownView(position, convertView, itemParent);
				TextView mTextView = (TextView) mView;
				if (isEnabled(position)) {
					GradeViewUtils.styleGradeLabel(mTextView,
							Globals.gradeToColorState(system.indexAt(position - gradeOffset)).getDefaultColor());
				} else {
					GradeViewUtils.styleGradeLabel(mTextView,
							Globals.gradeToColorState(system.indexAt(position - gradeOffset)).withAlpha(100).getDefaultColor(),
							Color.GRAY);
				}
				return mView;
			}
		};
		minSpinner.setAdapter(adapter);
		minSpinner.setSelection(system.positionOf(minSel) + idOffset, false); //+1 to accommodate for unknown string.

		adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
			// Disable click item < month current
			@Override
			public boolean isEnabled(int position) {
				if (addUnknown && position == 0) {
					return true;
				}
				return (minSpinner.getSelectedItemPosition() == 0
						|| position >= minSpinner.getSelectedItemPosition());
			}

			// Change color item
			@Override
			public View getDropDownView(int position, View convertView,
			                            @NonNull ViewGroup itemParent) {
				View mView = super.getDropDownView(position, convertView, itemParent);
				TextView mTextView = (TextView) mView;
				if (isEnabled(position)) {
					GradeViewUtils.styleGradeLabel(mTextView,
							Globals.gradeToColorState(system.indexAt(position - gradeOffset)).getDefaultColor());
				} else {
					GradeViewUtils.styleGradeLabel(mTextView,
							Globals.gradeToColorState(system.indexAt(position - gradeOffset)).withAlpha(100).getDefaultColor(),
							Color.GRAY);
				}
				return mView;
			}
		};
		maxSpinner.setAdapter(adapter);
		maxSpinner.setSelection(system.positionOf(maxSel) + idOffset, false);
	}

	public static int getGradeID(Spinner dropdownGrade, boolean handleOffsetIndex) {
		int position = dropdownGrade.getSelectedItemPosition();
		if (handleOffsetIndex && ((String) dropdownGrade.getItemAtPosition(0)).equalsIgnoreCase(ClimbingTags.UNKNOWN_GRADE_STRING)) {
			position -= WITH_UNKNOWN_INDEX_OFFSET;
		}
		return getGradeSystem(dropdownGrade.getContext()).indexAt(position);
	}
}
