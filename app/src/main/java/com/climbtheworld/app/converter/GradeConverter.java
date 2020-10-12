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

	private Spinner dropdownSystem;
	private Spinner dropdownGrade;
	private BaseAdapter listAdapter = new BaseAdapter() {
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
				if (view.getParent().getParent() instanceof RelativeLayout) {
					((RelativeLayout) view.getParent().getParent()).setBackgroundColor(Globals.gradeToColorState(i).getDefaultColor());
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
