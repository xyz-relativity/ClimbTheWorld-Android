package com.climbtheworld.app.utils.views;

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

	public static void updateGradeSpinner(AppCompatActivity parent, Spinner dropdownGrade, GeoNode node, boolean addUnknown) {
		int idOffset = NO_UNKNOWN_INDEX_OFFSET;

		List<String> allGrades = new ArrayList<String>();
		if (addUnknown) {
			allGrades.add(GeoNode.UNKNOWN_GRADE_STRING);
			idOffset = WITH_UNKNOWN_INDEX_OFFSET;
		}
		allGrades.addAll(GradeSystem.fromString(Configs.instance(parent).getString(Configs.ConfigKey.usedGradeSystem)).getAllGrades());
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
			// Change color item
			@Override
			public View getDropDownView(int position, View convertView,
			                            @NonNull ViewGroup itemParent) {
				View mView = super.getDropDownView(position, convertView, itemParent);
				TextView mTextView = (TextView) mView;

				mTextView.setBackgroundColor(Globals.gradeToColorState(position).getDefaultColor());
				return mView;
			}
		};

		dropdownGrade.setAdapter(adapter);
		dropdownGrade.setSelection(node.getLevelId(GeoNode.KEY_GRADE_TAG) + idOffset, false);
	}

	public static void updateLinkedGradeSpinners(AppCompatActivity parent, final Spinner minSpinner, int minSel, final Spinner maxSpinner, int maxSel, final boolean addUnknown, boolean offsetSelect) {
		int idOffset = NO_UNKNOWN_INDEX_OFFSET;

		List<String> allGrades = new ArrayList<String>();
		if (addUnknown) {
			allGrades.add(GeoNode.UNKNOWN_GRADE_STRING);
		}
		if (offsetSelect) {
			idOffset = WITH_UNKNOWN_INDEX_OFFSET;
		}
		allGrades.addAll(GradeSystem.fromString(Configs.instance(parent).getString(Configs.ConfigKey.usedGradeSystem)).getAllGrades());

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
					mTextView.setTextColor(Color.BLACK);
					mTextView.setBackgroundColor(Globals.gradeToColorState(position).getDefaultColor());
				} else {
					mTextView.setTextColor(Color.GRAY);
					mTextView.setBackgroundColor(Globals.gradeToColorState(position).withAlpha(100).getDefaultColor());
				}
				return mView;
			}
		};
		minSpinner.setAdapter(adapter);
		minSpinner.setSelection(minSel + idOffset, false); //+1 to accommodate for unknown string.

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
					mTextView.setTextColor(Color.BLACK);
					mTextView.setBackgroundColor(Globals.gradeToColorState(position).getDefaultColor());
				} else {
					mTextView.setTextColor(Color.GRAY);
					mTextView.setBackgroundColor(Globals.gradeToColorState(position).withAlpha(100).getDefaultColor());
				}
				return mView;
			}
		};
		maxSpinner.setAdapter(adapter);
		maxSpinner.setSelection(maxSel + idOffset, false);
	}

	public static int getGradeID(Spinner dropdownGrade, boolean handleOffsetIndex) {
		if (handleOffsetIndex) {
			if (((String) dropdownGrade.getItemAtPosition(0)).equalsIgnoreCase(GeoNode.UNKNOWN_GRADE_STRING)) {
				return dropdownGrade.getSelectedItemPosition() - WITH_UNKNOWN_INDEX_OFFSET;
			} else {
				return dropdownGrade.getSelectedItemPosition() - NO_UNKNOWN_INDEX_OFFSET;
			}
		} else {
			return dropdownGrade.getSelectedItemPosition();
		}
	}
}
