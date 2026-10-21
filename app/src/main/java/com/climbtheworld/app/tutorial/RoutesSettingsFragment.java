package com.climbtheworld.app.tutorial;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;

public class RoutesSettingsFragment extends TutorialFragment implements AdapterView.OnItemSelectedListener {

	private Configs configs;

	public RoutesSettingsFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		super.onCreate(view);
		configs = Configs.instance(parent);
		setMessage(view, getString(R.string.tutorial_routes_setup_message));

		//route settings
		Spinner dropdown = view.findViewById(R.id.gradeSelectSpinner);
		dropdown.setOnItemSelectedListener(null);
		dropdown.setAdapter(new GradeSystem.GradeSystemArrayAdapter(parent.get(), android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));
		dropdown.setSelection(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
		dropdown.setOnItemSelectedListener(this);
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		if (parent.getId() == R.id.gradeSelectSpinner) {
			configs.setString(Configs.ConfigKey.usedGradeSystem, GradeSystem.printableValues()[position].getMainKey());
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {

	}
}
