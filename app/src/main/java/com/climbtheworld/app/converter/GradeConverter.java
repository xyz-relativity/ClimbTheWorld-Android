package com.climbtheworld.app.converter;

import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.UnitConverterGradesAdvancedActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;

import java.util.List;

public class GradeConverter extends ConverterFragment {
	public static final String[] uiaaGrades =   new String[]{"1-",  "1",   "1+",  "2-",  "2",   "2+",  "3-",  "3",   "3+",  "4-",   "4",    "4+",   "5-",     "5",     "5+",      "6-",    "6",      "6+",    "7-",    "7",     "7+",    "8-",    "8",     "8+",    "9-",    "9",     "9+",    "10-",   "10",     "10+",   "11-",   "11",     "11+",      "12+",     "12",    "12+",       "13-",    "13",    "13+",   "14-"};
	public static final String[] ukTechGrades = new String[]{"1",   "1",   "1",   "2",   "2",   "2",   "3",   "3",   "3",   "4a",   "4a",   "4a",   "4a/4b",  "4b",    "4c",      "4c/5a", "5a",     "5a/5b", "5b",    "5b/5c", "5c",    "5c/6a", "6a",    "6a",    "6b",    "6b/6c", "6c",    "6c",    "6c/7a",  "7a",    "7a",    "7a/7b",  "7b",       "7b",      "7b",    ">7b",       ">7b",    ">7b",   ">7b",   ">7b"};
	public static final String[] ukAdjGrades =  new String[]{"M",   "M",   "M",   "M/D", "M/D", "M/D", "D",   "D",   "D",   "D/VD", "D/VD", "VD",   "S",      "HS",    "HS/VS",   "VS",    "HVS",    "E1",    "E1/E2", "E2",    "E2/E3", "E3",    "E4",    "E4/E5", "E5",    "E6",    "E6/E7", "E7",    "E7/E8",  "E8",    "E9",    "E9/E10", "E10",      "E11",     "E11",   ">E11",      ">E11",   ">E11",  ">E11",  ">E11"};
	public static final String[] fbGrades =     new String[]{"1",   "1",   "1",   "1",   "1",   "1",   "1/2", "1/2", "1/2", "2",    "2",    "2",    "2/3",    "3",     "4a",      "4a/4b", "4b",     "4c",    "5a",    "5b",    "5c",    "6a",    "6b",    "6b+",   "6c",    "6c+",   "7a",    "7a+",   "7a+/7b", "7b",    "7b+",   "7c",     "7c+",      "7c+/8a",  "8a",    "8a+/8b",    "8b",     "8b+",   "8c",    "8c+"};
	public static final String[] frenchGrades = new String[]{"1",   "1",   "1",   "2",   "2",   "2",   "3",   "3",   "3",   "4",    "4",    "4+",   "5a",     "5a/5b", "5b",      "5b/5c", "5c",     "6a",    "6a+",   "6b",    "6b+",   "6c",    "7a",    "7a+",   "7b+",   "7c",    "7c+",   "8a",    "8a/8a+", "8a+",   "8b",    "8b+",    "8c",       "8c+",     "9a",    "9a+",       "9a+/9b", "9b",    "9b+",   "9c"};
	public static final String[] saxonGrades =  new String[]{"I",   "I",   "I",   "II",  "II",  "II",  "III", "III", "III", "IV",   "IV",   "IV/V", "V",      "VI",    "VI/VIIa", "VIIa",  "VIIb",   "VIIc",  "VIIIa", "VIIIb", "VIIIc", "IXa",   "IXb",   "IXc",   "Xa",    "Xb",    "Xc",    "Xc",    "Xc/XIa", "XIa",   "XIb",   "XIc",    "XIc/XIIa", "XIIa",    "XIIb",  "XIIb/XIIc", "XIIc",   "XIIc",  ">XIIc", ">XIIc"};
	public static final String[] nordicGrades = new String[]{"1",   "1",   "1",   "1",   "1",   "1",   "1/2", "1/2", "1/2", "2",    "2",    "2",    "2/3",    "3",     "4a",      "4a/4b", "4b",     "4c",    "5a",    "5b",    "5c",    "6a",    "6b",    "6b+",   "6c",    "6c+",   "7a",    "7a+",   "7a+/7b", "7b",    "7b+",   "7c",     "7c+",      "7c+/8a",  "8a",    "8a+/8b",    "8b",     "8b+",   "8c",    "8c+"};
	public static final String[] ydsGrades =    new String[]{"5",   "5",   "5",   "5.1", "5.1", "5.2", "5.2", "5.3", "5.3", "5.4",  "5.5",  "5.6",  "5.7",    "5.8",   "5.9",     "5.10a", "5.10b",  "5.10c", "5.10d", "5.11a", "5.11b", "5.11c", "5.11d", "5.12a", "5.12b", "5.12c", "5.12d", "5.13a", "5.13b",  "5.13c", "5.13d", "5.14a",  "5.14b",    "5.14c",   "5.14d", "5.15a",     "5.15a",  "5.15b", "5.15c", "5.15d"};
	public static final String[] vGradeGrades = new String[]{"VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-",  "VB-",  "VB-",  "VB-/VB", "VB",    "VB/V0-",  "V0-",   "V0-/V0", "V0",    "V0+",   "V1",    "V1/V2", "V2",    "V3",    "V3/V4", "V4",    "V4/V5", "V5",    "V6",    "V6/V7",  "V7",    "V8",    "V9",     "V10",      "V10/V11", "V11",   "V12",       "V13",    "V14",   "V15",   "V15"};
	public static final String[] wiGrades =     new String[]{"WI2", "WI2", "WI2", "WI2", "WI2", "WI2", "WI2", "WI3", "WI3", "WI3",  "WI3",  "WI3",  "WI3",    "WI4",   "WI5",     "WI6",   "WI6",    "WI6",   "WI6",   "WI7",   "WI7",   "WI8",   "WI8",   "WI8",   "WI8",   "WI9",   "WI9",   "WI9",   "WI9",    "WI10",  "WI10",  "WI10",   "WI10",     "WI11",    "WI11",  "WI11",      "WI11",   "WI12",  "WI13",  "WI13"};
	public static final String[] mixedGrades =  new String[]{"M2",  "M2",  "M2",  "M2",  "M2",  "M2",  "M2",  "M3",  "M3",  "M3",   "M3",   "M3",   "M3",     "M4",    "M5",      "M6",    "M6",     "M6",    "M6",    "M7",    "M7",    "M8",    "M8",    "M8",    "M8",    "M9",    "M9",    "M9",    "M9",     "M10",   "M10",   "M10",    "M10",      "M11",     "M11",   "M11",       "M11",    "M12",   "M13",   "M13"};

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
			((TextView) view.findViewById(R.id.unitValue)).setText(GradeSystem.printableValues()[i].getGrade(dropdownGrade.getSelectedItemPosition()));
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
			                            ViewGroup itemParent) {
				View mView = super.getDropDownView(position, convertView, itemParent);
				TextView mTextView = (TextView) mView;

				mTextView.setBackgroundColor(Globals.gradeToColorState(position).getDefaultColor());
				return mView;
			}
		};

		dropdownGrade.setAdapter(adapter);
		dropdownGrade.setSelection(configs.getInt(Configs.ConfigKey.converterGradeValue));
		dropdownGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				configs.setInt(Configs.ConfigKey.converterGradeValue, i);
				if (view != null && view.getParent() != null && view.getParent().getParent() != null) {
					if (view.getParent().getParent() instanceof RelativeLayout) {
						((RelativeLayout) view.getParent().getParent()).setBackgroundColor(Globals.gradeToColorState(i).getDefaultColor());
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
}
