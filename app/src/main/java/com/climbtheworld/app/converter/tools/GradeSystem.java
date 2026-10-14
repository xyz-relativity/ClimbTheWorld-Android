package com.climbtheworld.app.converter.tools;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.GradeConverter;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public enum GradeSystem {
	uiaa("UIAA|Germany", "uiaa", R.string.grade_system_uiaa, R.string.grade_system_uiaa_short, R.string.grade_system_uiaa_description, GradeConverter.uiaaGrades),
	ukTech("UK Tech", "british_technical", R.string.grade_system_uk_tech, R.string.grade_system_uk_tech_short, R.string.grade_system_uk_tech_description, GradeConverter.ukTechGrades),
	ukAdj("UK ADJ", "british_adjectival", R.string.grade_system_uk_adj, R.string.grade_system_uk_adj_short, R.string.grade_system_uk_adj_description, GradeConverter.ukAdjGrades, GradeConverter.ukAdjGradeAliases),
	french("French", "french", R.string.grade_system_french, R.string.grade_system_french_short, R.string.grade_system_french_description, GradeConverter.frenchGrades, GradeConverter.frenchGradeAliases),
	saxon("Saxon|Swiss", "saxon", R.string.grade_system_saxon, R.string.grade_system_saxon_short, R.string.grade_system_saxon_description, GradeConverter.saxonGrades, GradeConverter.saxonGradeAliases),
	nordic("Nordic|Scandinavian", "norwegian", R.string.grade_system_nordic, R.string.grade_system_nordic_short, R.string.grade_system_nordic_description, GradeConverter.nordicGrades),
	polish("Polish", "polish", R.string.grade_system_polish, R.string.grade_system_polish_short, R.string.grade_system_polish_description, GradeConverter.polishGrades, GradeConverter.polishGradeAliases),
	yds("YDS|YDS_class", "yds_class|yds", R.string.grade_system_yds, R.string.grade_system_yds_short, R.string.grade_system_yds_description, GradeConverter.ydsGrades, GradeConverter.ydsGradeAliases),
	vGrade("V Grade|hueco", "hueco", R.string.grade_system_v_grade, R.string.grade_system_v_grade_short, R.string.grade_system_v_grade_description, GradeConverter.vGradeGrades, GradeConverter.vGradeAliases),
	font("Font", "font|fb|fontainebleau", R.string.grade_system_font, R.string.grade_system_font_short, R.string.grade_system_font_description, GradeConverter.fontGrades, GradeConverter.fontGradeAliases),
	undef("undefined", "", R.string.grade_system_undefined, R.string.grade_system_undefined_short, R.string.grade_system_undefined_description,   new String[]{});

	public static int maxGrades = uiaa.data.length;

	public String key;
	/** The &lt;system&gt; names of climbing:grade:&lt;system&gt; tags in this system. */
	private final String[] osmKeys;
	public int localeName;
	public int shortName;
	public int description;
	/** The grade starting on each row of the grade table, blank where the one above continues. */
	public final String[] data;
	/** The grades, easiest first, the row each starts on, and the one each row shows. */
	private final List<String> grades = new ArrayList<>();
	private final List<Integer> gradeRows = new ArrayList<>();
	private final int[] rowPositions;
	/** Pairs of a grade read as the other grade of data, but never written. */
	private final String[] readAliases;

	GradeSystem(String key, String osmKeys, int localeName, int shortName, int description, String[] data) {
		this(key, osmKeys, localeName, shortName, description, data, new String[]{});
	}

	GradeSystem(String key, String osmKeys, int localeName, int shortName, int description, String[] data, String[] readAliases) {
		this.key = key;
		this.osmKeys = osmKeys.isEmpty() ? new String[]{} : osmKeys.split("\\|");
		this.localeName = localeName;
		this.shortName = shortName;
		this.description = description;
		this.data = data;
		this.readAliases = readAliases;

		rowPositions = new int[data.length];
		for (int i = 0; i < data.length; ++i) {
			if (!data[i].isEmpty()) {
				grades.add(data[i]);
				gradeRows.add(i);
			}
			// The rows below the easiest grade show it too.
			rowPositions[i] = Math.max(grades.size() - 1, 0);
		}
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

	/**
	 * The system saved under systemString, or the standard system for a name the app doesn't
	 * know, such as one of a system it no longer has.
	 */
	public static GradeSystem fromString(String systemString) {
		for (GradeSystem checkSystem : GradeSystem.values()) {
			String[] keys = checkSystem.key.split("\\|");
			for (String key : keys) {
				if (key.equalsIgnoreCase(systemString)) {
					return checkSystem;
				}
			}
		}
		return UIConstants.STANDARD_SYSTEM;
	}

	/**
	 * The system of a climbing:grade:&lt;system&gt; tag, by the names the OSM wiki gives them,
	 * or undef for one the app can't read. Unlike fromString, climbing:grade:fb is Font: the wiki
	 * lists it as a misspelling of climbing:grade:font.
	 */
	public static GradeSystem fromOsmKey(String osmKey) {
		for (GradeSystem checkSystem : GradeSystem.values()) {
			for (String key : checkSystem.osmKeys) {
				if (key.equalsIgnoreCase(osmKey)) {
					return checkSystem;
				}
			}
		}
		return undef;
	}

	public String getGrade(int index) {
		if (index >= 0 && index < data.length) {
			return grades.get(rowPositions[index]);
		} else {
			return ClimbingTags.UNKNOWN_GRADE_STRING;
		}
	}

	public String buildExample() {
		int start = positionOf(uiaa.indexOf("6+"));
		return grades.get(start) + ", " + grades.get(start + 1) + ", " + grades.get(start + 2) + "...";
	}

	public String getMainKey() {
		return key.split("\\|")[0];
	}

	/**
	 * The row a grade starts on, or -1 for one the system doesn't have. A grade between two, like
	 * 7+/8- or 5.10b/c, is read as the harder one.
	 */
	public int indexOf(String grade) {
		String value = grade.trim();
		int result = rowOf(value);
		int slash = value.indexOf('/');
		if (result >= 0 || slash < 0) {
			return result;
		}

		String easier = value.substring(0, slash).trim();
		String harder = value.substring(slash + 1).trim();
		int harderRow = rowOf(harder);
		// The harder one may be written as the end of the easier one, like the c of 5.10b/c.
		for (int keep = easier.length(); harderRow < 0 && keep > 0; --keep) {
			harderRow = rowOf(easier.substring(0, keep) + harder);
		}
		return Math.max(rowOf(easier), harderRow);
	}

	private int rowOf(String grade) {
		if (grade.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < grades.size(); ++i) {
			if (grades.get(i).equalsIgnoreCase(grade)) {
				return gradeRows.get(i);
			}
		}
		for (int i = 0; i + 1 < readAliases.length; i += 2) {
			if (readAliases[i].equalsIgnoreCase(grade)) {
				return rowOf(readAliases[i + 1]);
			}
		}
		return -1;
	}

	/** The system's grades, easiest first, each once. */
	public List<String> getAllGrades() {
		return Collections.unmodifiableList(grades);
	}

	/** The position in getAllGrades of the grade shown on a row, or -1 for an unknown grade. */
	public int positionOf(int index) {
		if (index < 0 || index >= rowPositions.length) {
			return -1;
		}
		return rowPositions[index];
	}

	/** The row the grade at a position of getAllGrades starts on, or -1 for none. */
	public int indexAt(int position) {
		if (position < 0 || position >= gradeRows.size()) {
			return -1;
		}
		return gradeRows.get(position);
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
