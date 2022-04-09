package com.climbtheworld.app.converter.tools;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public enum GradeSystem {
	uiaa("UIAA|Germany", R.string.grade_system_uiaa, R.string.grade_system_uiaa_short, R.string.grade_system_uiaa_description, Constants.uiaaGrades),
	ukTech("UK Tech", R.string.grade_system_uk_tech, R.string.grade_system_uk_tech_short, R.string.grade_system_uk_tech_description, Constants.ukTechGrades),
	ukAdj("UK ADJ", R.string.grade_system_uk_adj, R.string.grade_system_uk_adj_short, R.string.grade_system_uk_adj_description, Constants.ukAdjGrades),
	fb("FB|French British", R.string.grade_system_fb, R.string.grade_system_fb_short, R.string.grade_system_fb_description, Constants.fbGrades),
	french("French", R.string.grade_system_french, R.string.grade_system_french_short, R.string.grade_system_french_description, Constants.frenchGrades),
	saxon("Saxon|Swiss", R.string.grade_system_saxon, R.string.grade_system_saxon_short, R.string.grade_system_saxon_description, Constants.saxonGrades),
	nordic("Nordic|Scandinavian", R.string.grade_system_nordic, R.string.grade_system_nordic_short, R.string.grade_system_nordic_description, Constants.nordicGrades),
	yds("YDS|YDS_class", R.string.grade_system_yds, R.string.grade_system_yds_short, R.string.grade_system_yds_description, Constants.ydsGrades),
	vGrade("V Grade", R.string.grade_system_v_grade, R.string.grade_system_v_grade_short, R.string.grade_system_v_grade_description, Constants.vGradeGrades),
	wi("WI", R.string.grade_system_wi, R.string.grade_system_wi_short, R.string.grade_system_wi_description, Constants.wiGrades),
	mixed("Mixed", R.string.grade_system_mixed, R.string.grade_system_mixed_short, R.string.grade_system_mixed_description, Constants.mixedGrades),
	undef("undefined", R.string.grade_system_undefined, R.string.grade_system_undefined_short, R.string.grade_system_undefined_description,   new String[]{});

	public static int maxGrades = uiaa.data.length;

	public String key;
	public int localeName;
	public int shortName;
	public int description;
	private final String[] data;

	GradeSystem(String key, int localeName, int shortName, int description, String[] data) {
		this.key = key;
		this.localeName = localeName;
		this.shortName = shortName;
		this.description = description;
		this.data = data;
	}

	public static GradeSystem[] printableValues() {
		List<GradeSystem> result = new ArrayList<>();
		for (GradeSystem checkSystem : GradeSystem.values()) {
			if (checkSystem != undef) {
				result.add(checkSystem);
			}
		}
		return result.toArray(new GradeSystem[0]);
	}

	public static GradeSystem fromString(String systemString) {
		for (GradeSystem checkSystem : GradeSystem.values()) {
			String[] keys = checkSystem.key.split("\\|");
			for (String key : keys) {
				if (key.equalsIgnoreCase(systemString)) {
					return checkSystem;
				}
			}
		}
		return undef;
	}

	public String getGrade(int index) {
		if (index >= 0 && index < data.length) {
			return data[index];
		} else {
			return ClimbingTags.UNKNOWN_GRADE_STRING;
		}
	}

	public String buildExample() {
		return data[16] + ", " + data[17] + ", " + data[18] + "...";
	}

	public String getMainKey() {
		return key.split("\\|")[0];
	}

	public int indexOf(String grade) {
		for (int i = 0; i < data.length; ++i) {
			if (data[i].equalsIgnoreCase(grade)) {
				return i;
			}
		}

		return -1;
	}

	public List<String> getAllGrades() {
		return Arrays.asList(data);
	}

	public static class GradeSystemArrayAdapter extends ArrayAdapter<GradeSystem> {

		Context context;

		public GradeSystemArrayAdapter(Context context, int resource, GradeSystem[] objects) {
			super(context, resource, objects);
			this.context = context;
		}

		@Override
		public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
			return ListViewItemBuilder.getPaddedBuilder(context, convertView, false)
					.setTitle(context.getString(Objects.requireNonNull(getItem(position)).localeName))
					.setDescription(context.getString(getItem(position).shortName) + ": " + getItem(position).buildExample())
					.build();
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			return ListViewItemBuilder.getNonPaddedBuilder(context, convertView, false)
					.setTitle(context.getString(Objects.requireNonNull(getItem(position)).localeName))
					.setDescription(context.getString(getItem(position).shortName) + ": " + getItem(position).buildExample())
					.build();
		}
	}
}
