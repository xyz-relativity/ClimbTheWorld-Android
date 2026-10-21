package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.ClimbingTags;

import java.util.function.IntConsumer;

public class SpinnerUtils {
	private static final int NO_UNKNOWN_INDEX_OFFSET = 0;
	private static final int WITH_UNKNOWN_INDEX_OFFSET = 1;

	private SpinnerUtils() {
		//hide
	}

	/** A listener for the position picked, as nothing is done when nothing is selected. */
	public static AdapterView.OnItemSelectedListener onItemSelected(IntConsumer onPosition) {
		return new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
				onPosition.accept(position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {
			}
		};
	}

	public static GradeSystem getGradeSystem(Context context) {
		return GradeSystem.fromString(Configs.instance(context).getString(Configs.ConfigKey.usedGradeSystem));
	}

	/** Lists system's grades in dropdownGrade, with the grade of levelId selected. */
	public static void updateGradeSpinner(AppCompatActivity parent, Spinner dropdownGrade, GradeSystem system, int levelId, boolean addUnknown) {
		int idOffset = addUnknown ? WITH_UNKNOWN_INDEX_OFFSET : NO_UNKNOWN_INDEX_OFFSET;

		dropdownGrade.setAdapter(new GradeArrayAdapter(parent, system, addUnknown));
		dropdownGrade.setSelection(system.positionOf(levelId) + idOffset, false);
	}

	public static void updateLinkedGradeSpinners(AppCompatActivity parent, final Spinner minSpinner, int minSel, final Spinner maxSpinner, int maxSel, final boolean addUnknown) {
		int idOffset = addUnknown ? WITH_UNKNOWN_INDEX_OFFSET : NO_UNKNOWN_INDEX_OFFSET;
		GradeSystem system = getGradeSystem(parent);

		minSpinner.setAdapter(new GradeArrayAdapter(parent, system, addUnknown) {
			// A minimum harder than the maximum can't be picked.
			@Override
			public boolean isEnabled(int position) {
				if (addUnknown && position == 0) {
					return true;
				}
				return (maxSpinner.getSelectedItemPosition() == 0)
						|| (position <= maxSpinner.getSelectedItemPosition());
			}
		});
		minSpinner.setSelection(system.positionOf(minSel) + idOffset, false); //+1 to accommodate for unknown string.

		maxSpinner.setAdapter(new GradeArrayAdapter(parent, system, addUnknown) {
			// A maximum easier than the minimum can't be picked.
			@Override
			public boolean isEnabled(int position) {
				if (addUnknown && position == 0) {
					return true;
				}
				return (minSpinner.getSelectedItemPosition() == 0
						|| position >= minSpinner.getSelectedItemPosition());
			}
		});
		maxSpinner.setSelection(system.positionOf(maxSel) + idOffset, false);
	}

	public static int getGradeID(Spinner dropdownGrade, boolean handleOffsetIndex) {
		return getGradeID(dropdownGrade, getGradeSystem(dropdownGrade.getContext()), handleOffsetIndex);
	}

	/** The grade id selected in a dropdownGrade that lists system's grades. */
	public static int getGradeID(Spinner dropdownGrade, GradeSystem system, boolean handleOffsetIndex) {
		int position = dropdownGrade.getSelectedItemPosition();
		if (handleOffsetIndex && ((String) dropdownGrade.getItemAtPosition(0)).equalsIgnoreCase(ClimbingTags.UNKNOWN_GRADE_STRING)) {
			position -= WITH_UNKNOWN_INDEX_OFFSET;
		}
		return system.indexAt(position);
	}
}
