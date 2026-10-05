package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.GradeViewUtils;

/**
 * Popup listing a grade in every grading system, opened by tapping a grade label. Any tap
 * closes it.
 */
public class GradeConversionDialogBuilder {
	private GradeConversionDialogBuilder() {
		//hide constructor
	}

	/**
	 * Opens the conversion popup when the label is tapped. Unknown grades are left as they are.
	 */
	public static void makeClickable(AppCompatActivity activity, TextView label, int grade) {
		makeClickable(activity, label, grade, grade);
	}

	/**
	 * Same as {@link #makeClickable(AppCompatActivity, TextView, int)}, for a label that covers
	 * a range of grade indexes, as merged grade rows do.
	 */
	public static void makeClickable(AppCompatActivity activity, TextView label, int minGrade,
	                                 int maxGrade) {
		if (minGrade < 0 || maxGrade < minGrade) {
			return;
		}

		TypedValue ripple = new TypedValue();
		activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
		label.setForeground(ContextCompat.getDrawable(activity, ripple.resourceId));
		label.setOnClickListener(view -> showGradeConversionDialog(activity, minGrade, maxGrade));
	}

	public static void showGradeConversionDialog(AppCompatActivity activity, int minGrade,
	                                             int maxGrade) {
		GradeSystem usedSystem = GradeSystem.fromString(
				Configs.instance(activity).getString(Configs.ConfigKey.usedGradeSystem));
		AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setTitle(activity.getString(R.string.grade_conversion,
				gradeText(activity, usedSystem, minGrade, maxGrade),
				activity.getString(usedSystem.shortName)));

		View result = activity.getLayoutInflater()
				.inflate(R.layout.dialog_grade_conversion, alertDialog.getListView(), false);
		LinearLayout container = result.findViewById(R.id.gradeConversionContainer);
		int color = Globals.gradeToColorState(minGrade).getDefaultColor();

		for (GradeSystem system : GradeSystem.printableValues()) {
			View row = activity.getLayoutInflater()
					.inflate(R.layout.list_item_grade_conversion, container, false);
			TextView name = row.findViewById(R.id.gradeSystemName);
			name.setText(system.localeName);
			if (system == usedSystem) {
				name.setTypeface(name.getTypeface(), Typeface.BOLD);
			}
			TextView value = row.findViewById(R.id.gradeSystemValue);
			value.setText(gradeText(activity, system, minGrade, maxGrade));
			GradeViewUtils.styleGradeLabel(value, color);
			container.addView(row);
		}

		alertDialog.setView(result);
		alertDialog.create();
		// The scroll view keeps its own touches, so the rows are closed from their container;
		// the decor view gets the taps on the title and margins that nothing else handles.
		container.setOnClickListener(view -> alertDialog.dismiss());
		alertDialog.getWindow().getDecorView().setOnClickListener(view -> alertDialog.dismiss());
		alertDialog.show();
	}

	private static String gradeText(AppCompatActivity activity, GradeSystem system, int minGrade,
	                                int maxGrade) {
		String min = system.getGrade(minGrade);
		String max = system.getGrade(maxGrade);
		return min.equals(max) ? min : activity.getString(R.string.grade_range, min, max);
	}
}
