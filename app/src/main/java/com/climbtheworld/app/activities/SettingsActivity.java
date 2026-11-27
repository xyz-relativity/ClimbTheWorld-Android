package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ArCoreFeatures;
import com.climbtheworld.app.configs.AugmentedRealityFragment;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.configs.DisplayFilterFragment;
import com.climbtheworld.app.configs.IntercomFragment;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.StickyHeaders;

public class SettingsActivity extends AppCompatActivity
		implements CompoundButton.OnCheckedChangeListener {

	private Configs configs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		configs = Configs.instance(this);
		uiSetup();

		// The header of the group scrolled into stays at the top.
		StickyHeaders.attach(findViewById(R.id.settingsScroll), findViewById(R.id.stickyGroupHeader));
	}

	private void uiSetup() {
		// General
		ConfigFragment.addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.keepScreenOn);

		// Downloads
		ConfigFragment.addSwitch(findViewById(R.id.linerLayoutMobileDataSettings), this, Configs.ConfigKey.useMobileDataForMap);
		ConfigFragment.addSwitch(findViewById(R.id.linerLayoutMobileDataSettings), this, Configs.ConfigKey.useMobileDataForRoutes);

		DisplayFilterFragment filter = new DisplayFilterFragment(this, findViewById(R.id.routesFiltersContainer));
		AugmentedRealityFragment filterAr = new AugmentedRealityFragment(this, findViewById(R.id.augmentedRealitySettingsContainer));
		IntercomFragment intercom = new IntercomFragment(this, findViewById(R.id.intercomSettingsContainer));

		// Only the non-FOSS build has ARCore. Its switch heads the A.R. section, which the A.R.
		// view's settings dialog shows without it.
		if (ArCoreFeatures.AVAILABLE) {
			ViewGroup arCoreSettings = findViewById(R.id.linerLayoutArCoreSettings);
			ConfigFragment.addSwitch(arCoreSettings, this, Configs.ConfigKey.useArCore);
			arCoreSettings.setVisibility(View.VISIBLE);
		}

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
			if (isChecked) {
				// The user wants ARCore again, whatever they refused before.
				configs.setInt(Configs.ConfigKey.arCoreViewRefusals, 0);
				configs.setBoolean(Configs.ConfigKey.arCoreViewRefusedThisRun, false);
				configs.setBoolean(Configs.ConfigKey.showArCoreInstall, true);
			}
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
