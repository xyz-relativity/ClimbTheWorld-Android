package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.AugmentedRealityFragment;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.configs.DisplayFilterFragment;
import com.climbtheworld.app.configs.IntercomFragment;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;

public class SettingsActivity extends AppCompatActivity
		implements CompoundButton.OnCheckedChangeListener {

	private Configs configs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		configs = Configs.instance(this);
		uiSetup();
	}

	private void uiSetup() {
		//Device settings
		ConfigFragment.addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.keepScreenOn);
//        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useArCore);
		ConfigFragment.addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForMap);
		ConfigFragment.addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForRoutes);

		DisplayFilterFragment filter = new DisplayFilterFragment(this, findViewById(R.id.routesFiltersContainer));
		AugmentedRealityFragment filterAr = new AugmentedRealityFragment(this, findViewById(R.id.augmentedRealitySettingsContainer));
		IntercomFragment intercom = new IntercomFragment(this, findViewById(R.id.intercomSettingsContainer));

		//route settings
		Spinner dropdown = findViewById(R.id.gradeSelectSpinner);
		dropdown.setOnItemSelectedListener(null);

		dropdown.setAdapter(new GradeSystem.GradeSystemArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

		dropdown.setSelection(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
		dropdown.setOnItemSelectedListener(filter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);
	}

	@Override
	protected void onPause() {
		Globals.onPause(this);
		super.onPause();
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (buttonView.getId() == Configs.ConfigKey.useArCore.stringId) {
			configs.setBoolean(Configs.ConfigKey.useArCore, isChecked);
		}

		if (buttonView.getId() == Configs.ConfigKey.keepScreenOn.stringId) {
			configs.setBoolean(Configs.ConfigKey.keepScreenOn, isChecked);
		}

		if (buttonView.getId() == Configs.ConfigKey.useMobileDataForMap.stringId) {
			configs.setBoolean(Configs.ConfigKey.useMobileDataForMap, isChecked);
		}

		if (buttonView.getId() == Configs.ConfigKey.useMobileDataForRoutes.stringId) {
			configs.setBoolean(Configs.ConfigKey.useMobileDataForRoutes, isChecked);
		}
	}
}
