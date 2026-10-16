package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

/**
 * The grades of a system for a grade dropdown, shown the same way in every one of them and as the
 * grade labels are everywhere else: the selected grade as wide as a grade label, and every grade
 * centred on its colour. An entry that a subclass's isEnabled refuses is shown dimmed.
 */
public class GradeArrayAdapter extends ArrayAdapter<String> {
	private final GradeSystem system;
	/** 1 when the list starts with the unknown grade, which is on no row of system. */
	private final int gradeOffset;

	public GradeArrayAdapter(Context context, GradeSystem system, boolean addUnknown) {
		super(context, R.layout.list_item_grade_spinner, grades(system, addUnknown));
		setDropDownViewResource(R.layout.list_item_grade_spinner_dropdown);
		this.system = system;
		this.gradeOffset = addUnknown ? 1 : 0;
	}

	private static List<String> grades(GradeSystem system, boolean addUnknown) {
		List<String> result = new ArrayList<>();
		if (addUnknown) {
			result.add(ClimbingTags.UNKNOWN_GRADE_STRING);
		}
		result.addAll(system.getAllGrades());
		return result;
	}

	@NonNull
	@Override
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		TextView label = (TextView) super.getView(position, convertView, parent);
		GradeViewUtils.styleGradeLabel(label, colorAt(position).getDefaultColor());
		return label;
	}

	@Override
	public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
		TextView label = (TextView) super.getDropDownView(position, convertView, parent);
		if (isEnabled(position)) {
			GradeViewUtils.styleGradeLabel(label, colorAt(position).getDefaultColor());
		} else {
			GradeViewUtils.styleGradeLabel(label, colorAt(position).withAlpha(100).getDefaultColor(),
					Color.GRAY);
		}
		return label;
	}

	private ColorStateList colorAt(int position) {
		return Globals.gradeToColorState(system.indexAt(position - gradeOffset));
	}
}
