package com.climbtheworld.app.converter;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.UnitConverterGradesAdvancedActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.GradeViewUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GradeConverter extends ConverterFragment {
	/**
	 * The grades of each system, easiest first, from the comparison tables of Wikipedia's
	 * "Grade (climbing)" article (Rockfax 2021, theCrag 2023, and the UIAA's 2016 report for
	 * bouldering); the comments say which cells are not from it. A row is the smallest step any of
	 * the systems makes, and its index is the grade id the app keeps. A grade sits on the row it
	 * starts on and a blank continues the grade above, the way Wikipedia merges cells, so a grade
	 * that falls between two of another system's shows as the easier one. V and Font grades sit on
	 * the YDS grade of Wikipedia's bouldering table, the first one where it gives a range. UIAA and
	 * Nordic have no half grades, so Wikipedia's Nordic 7+/8- cells are left blank.
	 */
	private static final String[][] GRADE_TABLE = {
		//UIAA   UK Tech UK Adj French Saxon    Nordic Polish   YDS      V      Font
		{"1-",  "",     "",    "",    "",      "",    "",      "3",     "VB-", "2"},                  // not in Wikipedia: UIAA 1-; YDS 3 and 4 split its 3-4
		{"1",   "1",    "M",   "1",   "I",     "1",   "I",     "4",     "",    ""},
		{"1+",  "",     "",    "",    "",      "",    "",      "5.0",   "",    ""},                   // not in Wikipedia: UIAA 1+
		{"2-",  "",     "",    "",    "",      "2-",  "",      "",      "",    ""},                   // not in Wikipedia: UIAA 2-, Nordic 2-
		{"2",   "2",    "",    "2",   "II",    "2",   "II",    "5.1",   "",    ""},
		{"2+",  "",     "D",   "",    "",      "2+",  "",      "5.2",   "",    ""},                   // not in Wikipedia: UIAA 2+, Nordic 2+
		{"3-",  "",     "",    "",    "",      "3-",  "III-",  "",      "",    ""},                   // not in Wikipedia: UIAA 3-, Nordic 3-, Polish III-
		{"3",   "3",    "",    "3",   "III",   "3",   "III",   "5.3",   "",    ""},
		{"3+",  "",     "",    "",    "",      "3+",  "III+",  "",      "",    ""},                   // not in Wikipedia: UIAA 3+, Nordic 3+, Polish III+
		{"4-",  "",     "",    "",    "",      "",    "",      "",      "",    ""},                   // not in Wikipedia: UIAA 4-
		{"4",   "",     "VD",  "4a",  "IV",    "4-",  "IV",    "5.4",   "VB",  "3"},                  // not in Wikipedia: Nordic 4-
		{"4+",  "4a",   "S",   "4b",  "V",     "4",   "",      "5.5",   "",    ""},                   // Wikipedia: IV+/V-
		{"5-",  "",     "",    "",    "",      "",    "",      "",      "",    ""},                   // not in Wikipedia: UIAA 5-
		{"5",   "4b",   "HS",  "4c",  "VI",    "4+",  "IV+",   "5.6",   "",    ""},                   // not in Wikipedia: Nordic 4+
		{"5+",  "4c",   "VS",  "5a",  "",      "5-",  "V-",    "5.7",   "",    ""},
		{"",    "",     "",    "5a+", "",      "",    "",      "",      "",    ""},                   // not in Wikipedia: French 5a+
		{"6-",  "",     "HVS", "5b",  "VIIa",  "5",   "V",     "5.8",   "V0-", "4-"},
		{"",    "",     "",    "5b+", "",      "",    "",      "",      "",    ""},                   // not in Wikipedia: French 5b+
		{"6",   "5a",   "",    "5c",  "VIIb",  "5+",  "V+",    "5.9",   "V0",  "4"},
		{"",    "",     "",    "5c+", "",      "",    "VI-",   "",      "",    ""},                   // not in Wikipedia: French 5c+, Polish VI-
		{"6+",  "",     "E1",  "6a",  "VIIc",  "6-",  "VI",    "5.10a", "V0+", "4+"},
		{"7-",  "5b",   "",    "6a+", "",      "",    "VI+",   "5.10b", "",    ""},
		{"7",   "",     "E2",  "6b",  "VIIIa", "6",   "VI.1",  "5.10c", "V1",  "5"},
		{"7+",  "5c",   "",    "6b+", "VIIIb", "6+",  "VI.1+", "5.10d", "V2",  "5+"},
		{"",    "",     "E3",  "6c",  "VIIIc", "7-",  "VI.2",  "5.11a", "",    ""},                   // Wikipedia: 6c/6c+, 7-/7, VI.2/VI.2+
		{"8-",  "",     "",    "",    "",      "",    "",      "5.11b", "",    ""},
		{"",    "6a",   "E4",  "6c+", "IXa",   "7",   "VI.2+", "5.11c", "V3",  "6A"},                 // Wikipedia: 6c/6c+, 7-/7, VI.2/VI.2+
		{"8",   "",     "",    "7a",  "IXb",   "7+",  "VI.3",  "5.11d", "",    "6A+"},
		{"8+",  "",     "E5",  "7a+", "IXc",   "",    "VI.3+", "5.12a", "V4",  "6B"},                 // Wikipedia: 7+/8-
		{"",    "",     "",    "",    "",      "",    "",      "",      "",    "6B+"},                // not in Wikipedia
		{"",    "6b",   "",    "7b",  "",      "8-",  "VI.4",  "5.12b", "V5",  "6C"},
		{"",    "",     "",    "",    "",      "",    "",      "",      "",    "6C+"},                // not in Wikipedia
		{"9-",  "",     "E6",  "7b+", "Xa",    "8",   "",      "5.12c", "V6",  "7A"},
		{"9",   "",     "",    "7c",  "Xb",    "",    "VI.4+", "5.12d", "V7",  "7A+"},                // Wikipedia: 8/8+
		{"9+",  "6c",   "",    "7c+", "Xc",    "8+",  "VI.5",  "5.13a", "V8",  "7B"},
		{"10-", "",     "E7",  "8a",  "XIa",   "9-",  "VI.5+", "5.13b", "",    "7B+"},
		{"",    "7a",   "",    "8a+", "",      "",    "",      "5.13c", "V9",  "7C"},                 // Wikipedia: 9-/9
		{"10",  "",     "E8",  "8b",  "XIb",   "9",   "VI.6",  "5.13d", "V10", "7C+"},
		{"10+", "",     "",    "8b+", "XIc",   "",    "VI.6+", "5.14a", "V11", "8A"},                 // Wikipedia: 9/9+
		{"11-", "7b",   "E9",  "8c",  "XIIa",  "9+",  "VI.7",  "5.14b", "V12", "8A+"},
		{"11",  "",     "",    "8c+", "XIIb",  "10-", "VI.7+", "5.14c", "V13", "8B"},
		{"11+", "",     "E10", "9a",  "XIIc",  "10",  "VI.8",  "5.14d", "V14", "8B+"},
		{"12-", "",     "E11", "9a+", "XIIIa", "",    "VI.8+", "5.15a", "V15", "8C"},                 // Wikipedia: 10/10+
		{"",    "",     "",    "9b",  "",      "10+", "",      "5.15b", "V16", "8C+"},
		{"12",  "",     "",    "9b+", "XIIIb", "11-", "VI.9",  "5.15c", "V17", "9A"},
		{"12+", "",     "",    "9c",  "XIIIc", "11",  "VI.9+", "5.15d", "V18", "9A+"},
	};

	public static final String[] uiaaGrades = column(0);
	public static final String[] ukTechGrades = column(1);
	public static final String[] ukAdjGrades = column(2);
	public static final String[] frenchGrades = column(3);
	public static final String[] saxonGrades = column(4);
	public static final String[] nordicGrades = column(5);
	public static final String[] polishGrades = column(6);
	public static final String[] ydsGrades = column(7);
	public static final String[] vGradeGrades = column(8);
	public static final String[] fontGrades = column(9);

	/** Pairs of a grade read as the other grade of the same table, but never written. */
	public static final String[] ukAdjGradeAliases = new String[]{"HVD", "VD"};
	/** Below 6a French grades are also written without a letter: 4- is 4a, 4 is 4b and 4+ is 4c. */
	public static final String[] frenchGradeAliases = new String[]{
			"1a", "1", "1b", "1", "1c", "1", "1+", "1",
			"2a", "2", "2b", "2", "2c", "2", "2-", "2", "2+", "2",
			"3a", "3", "3b", "3", "3c", "3", "3-", "3", "3+", "3",
			"4-", "4a", "4", "4b", "4+", "4c", "4a+", "4a", "4b+", "4b", "4c+", "4c",
			"5-", "5a", "5", "5b", "5+", "5c"};
	/** Saxon grades written without their letter are read as the middle one. */
	public static final String[] saxonGradeAliases = new String[]{"VII", "VIIb", "VIII", "VIIIb",
			"IX", "IXb", "X", "Xb", "XI", "XIb", "XII", "XIIb", "XIII", "XIIIb"};
	public static final String[] polishGradeAliases = new String[]{"VI+/1", "VI.1", "IV-", "IV"};
	/** From 5.10 a grade without its letter is the b one, a - the a and a + the d; below, +/- go. */
	public static final String[] ydsGradeAliases = ydsGradeAliases();
	/** V grades with a + or -, other than V0's, are read without it. */
	public static final String[] vGradeAliases = vGradeAliases();
	public static final String[] fontGradeAliases = new String[]{"2+", "2", "3+", "3", "5-", "5"};

	private Spinner dropdownSystem;
	private Spinner dropdownGrade;
	private final BaseAdapter listAdapter = new BaseAdapter() {
		@Override
		public int getCount() {
			return GradeSystem.printableValues().length;
		}

		@Override
		public Object getItem(int i) {
			return GradeSystem.printableValues()[i];
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			if (view == null) {
				view = inflater.inflate(R.layout.list_item_converter, viewGroup, false);
			}
			((TextView) view.findViewById(R.id.unitValue)).setText(GradeSystem.printableValues()[i].getGrade(configs.getInt(Configs.ConfigKey.converterGradeValue)));
			((TextView) view.findViewById(R.id.systemValue)).setText(parent.getString(GradeSystem.printableValues()[i].shortName));
			return view;
		}
	};
	private LayoutInflater inflater;

	public GradeConverter(AppCompatActivity parent, @LayoutRes int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		this.view = view;
		inflater = parent.getLayoutInflater();

		((TextView) findViewById(R.id.gradingSelectLabel)).setText(parent.getResources().getString(R.string.grade_system,
				parent.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.converterGradeSystem)).shortName)));

		dropdownSystem = findViewById(R.id.gradeSystemSpinner);
		dropdownGrade = findViewById(R.id.gradeSelectSpinner);

		dropdownSystem.setOnItemSelectedListener(null);
		dropdownSystem.setAdapter(new GradeSystem.GradeSystemArrayAdapter(parent, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

		int selectLocation = GradeSystem.fromString(configs.getString(Configs.ConfigKey.converterGradeSystem)).ordinal();
		if (selectLocation < dropdownSystem.getAdapter().getCount()) {
			dropdownSystem.setSelection(selectLocation, false);
		}
		dropdownSystem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				configs.setString(Configs.ConfigKey.converterGradeSystem, GradeSystem.printableValues()[i].getMainKey());
				((TextView) findViewById(R.id.gradingSelectLabel)).setText(parent.getResources().getString(R.string.grade_system,
						parent.getResources().getString(GradeSystem.printableValues()[i].shortName)));
				buildGradeDropdown(GradeSystem.printableValues()[i]);
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});

		buildGradeDropdown(GradeSystem.printableValues()[dropdownSystem.getSelectedItemPosition()]);

		ListView resultsList = findViewById(R.id.listGradesConverter);
		resultsList.setAdapter(listAdapter);

		findViewById(R.id.buttonShowGradesTable).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(parent, UnitConverterGradesAdvancedActivity.class);
				parent.startActivity(intent);
			}
		});
	}

	private void buildGradeDropdown(GradeSystem system) {
		Spinner dropdownGrade = findViewById(R.id.gradeSelectSpinner);
		List<String> allGrades = system.getAllGrades();
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
			// Change color item
			@Override
			public View getDropDownView(int position, View convertView,
			                            @NonNull ViewGroup itemParent) {
				View mView = super.getDropDownView(position, convertView, itemParent);
				TextView mTextView = (TextView) mView;

				GradeViewUtils.styleGradeLabel(mTextView,
						Globals.gradeToColorState(system.indexAt(position)).getDefaultColor());
				return mView;
			}

			// The selected row sits on the grade colour painted on its container below, so its
			// label needs the same treatment as the dropdown entries.
			@NonNull
			@Override
			public View getView(int position, View convertView, @NonNull ViewGroup itemParent) {
				View mView = super.getView(position, convertView, itemParent);
				GradeViewUtils.styleGradeText((TextView) mView, Color.BLACK);
				return mView;
			}
		};

		dropdownGrade.setAdapter(adapter);
		dropdownGrade.setSelection(Math.max(system.positionOf(configs.getInt(Configs.ConfigKey.converterGradeValue)), 0));
		dropdownGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				// Switching systems selects the grade shown for the kept one, which may start on an
				// easier row: keeping the row lets switching back show the grade picked before.
				if (system.positionOf(configs.getInt(Configs.ConfigKey.converterGradeValue)) != i) {
					configs.setInt(Configs.ConfigKey.converterGradeValue, system.indexAt(i));
				}
				int grade = configs.getInt(Configs.ConfigKey.converterGradeValue);
				if (view != null && view.getParent() != null && view.getParent().getParent() != null) {
					if (view.getParent().getParent() instanceof RelativeLayout) {
						((RelativeLayout) view.getParent().getParent()).setBackgroundColor(Globals.gradeToColorState(grade).getDefaultColor());
					}
				}
				listAdapter.notifyDataSetChanged();
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});
	}

	@Override
	public void onViewSelected() {
		hideKeyboard();
	}

	private static String[] column(int index) {
		String[] result = new String[GRADE_TABLE.length];
		for (int i = 0; i < GRADE_TABLE.length; ++i) {
			result[i] = GRADE_TABLE[i][index];
		}
		return result;
	}

	private static String[] ydsGradeAliases() {
		List<String> result = new ArrayList<>();
		for (int i = 0; i < 10; ++i) {
			result.addAll(Arrays.asList("5." + i + "-", "5." + i, "5." + i + "+", "5." + i));
		}
		for (int i = 10; i <= 15; ++i) {
			result.addAll(Arrays.asList("5." + i, "5." + i + "b", "5." + i + "-", "5." + i + "a",
					"5." + i + "+", "5." + i + "d"));
		}
		return result.toArray(new String[0]);
	}

	private static String[] vGradeAliases() {
		List<String> result = new ArrayList<>();
		for (int i = 1; i <= 18; ++i) {
			result.addAll(Arrays.asList("V" + i + "-", "V" + i, "V" + i + "+", "V" + i));
		}
		return result.toArray(new String[0]);
	}
}
