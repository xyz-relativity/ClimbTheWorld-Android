package com.climbtheworld.app.converter.tools;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.GradeConverter;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public enum GradeSystem {
	uiaa("UIAA|Germany", "uiaa", R.string.grade_system_uiaa, R.string.grade_system_uiaa_short, R.string.grade_system_uiaa_description, GradeConverter.uiaaGrades),
	ukTech("UK Tech", "british_technical", R.string.grade_system_uk_tech, R.string.grade_system_uk_tech_short, R.string.grade_system_uk_tech_description, GradeConverter.ukTechGrades),
	ukAdj("UK ADJ", "british_adjectival", R.string.grade_system_uk_adj, R.string.grade_system_uk_adj_short, R.string.grade_system_uk_adj_description, GradeConverter.ukAdjGrades, GradeConverter.ukAdjGradeAliases),
	fb("FB|French British", "", R.string.grade_system_fb, R.string.grade_system_fb_short, R.string.grade_system_fb_description, GradeConverter.fbGrades),
	french("French", "french", R.string.grade_system_french, R.string.grade_system_french_short, R.string.grade_system_french_description, GradeConverter.frenchGrades),
	saxon("Saxon|Swiss", "saxon", R.string.grade_system_saxon, R.string.grade_system_saxon_short, R.string.grade_system_saxon_description, GradeConverter.saxonGrades),
	nordic("Nordic|Scandinavian", "norwegian", R.string.grade_system_nordic, R.string.grade_system_nordic_short, R.string.grade_system_nordic_description, GradeConverter.nordicGrades),
	polish("Polish", "polish", R.string.grade_system_polish, R.string.grade_system_polish_short, R.string.grade_system_polish_description, GradeConverter.polishGrades, GradeConverter.polishGradeAliases),
	ewbank("Ewbank", "ewbank|ewbanks", R.string.grade_system_ewbank, R.string.grade_system_ewbank_short, R.string.grade_system_ewbank_description, GradeConverter.ewbankGrades, GradeConverter.ewbankGradeAliases),
	yds("YDS|YDS_class", "yds_class|yds", R.string.grade_system_yds, R.string.grade_system_yds_short, R.string.grade_system_yds_description, GradeConverter.ydsGrades),
	vGrade("V Grade|hueco", "hueco", R.string.grade_system_v_grade, R.string.grade_system_v_grade_short, R.string.grade_system_v_grade_description, GradeConverter.vGradeGrades),
	font("Font", "font|fb|fontainebleau", R.string.grade_system_font, R.string.grade_system_font_short, R.string.grade_system_font_description, GradeConverter.fontGrades, GradeConverter.fontGradeAliases),
	wi("WI", "ice", R.string.grade_system_wi, R.string.grade_system_wi_short, R.string.grade_system_wi_description, GradeConverter.wiGrades, GradeConverter.wiGradeAliases),
	mixed("Mixed", "mixed", R.string.grade_system_mixed, R.string.grade_system_mixed_short, R.string.grade_system_mixed_description, GradeConverter.mixedGrades),
	undef("undefined", "", R.string.grade_system_undefined, R.string.grade_system_undefined_short, R.string.grade_system_undefined_description,   new String[]{});

	public static int maxGrades = uiaa.data.length;

	public String key;
	/** The &lt;system&gt; names of climbing:grade:&lt;system&gt; tags in this system. */
	private final String[] osmKeys;
	public int localeName;
	public int shortName;
	public int description;
	public final String[] data;
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
		for (int i = 0; i + 1 < readAliases.length; i += 2) {
			if (readAliases[i].equalsIgnoreCase(grade)) {
				return indexOf(readAliases[i + 1]);
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
