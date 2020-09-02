package com.climbtheworld.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Spinner;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.AugmentedRealityFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.configs.DisplayFilterFragment;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ListViewItemBuilder;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity
        implements CompoundButton.OnCheckedChangeListener {

    private Configs configs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        configs = Configs.instance(this);
        uiSetup();
    }

    private void uiSetup() {
        //Device settings
        addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.keepScreenOn);
//        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useArCore);
        addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForMap);
        addSwitch(findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForRoutes);

        DisplayFilterFragment filter = new DisplayFilterFragment(this, findViewById(R.id.routesFiltersContainer));
        AugmentedRealityFragment filterAr = new AugmentedRealityFragment(this, findViewById(R.id.augmentedRealitySettingsContainer));

        //route settings
        Spinner dropdown = findViewById(R.id.gradeSelectSpinner);
        dropdown.setOnItemSelectedListener(null);

        dropdown.setAdapter(new GradeSystem.GradeSystemArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

        dropdown.setSelection(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
        dropdown.setOnItemSelectedListener(filter);

        addSwitch((ViewGroup)findViewById(R.id.linerLayoutRouteSettings), this, Configs.ConfigKey.showVirtualHorizon);
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
        if (buttonView.getId() == Configs.ConfigKey.showVirtualHorizon.stringId) {
            configs.setBoolean(Configs.ConfigKey.showVirtualHorizon, isChecked);
        }

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

    private void addSwitch(ViewGroup viewContainer, CompoundButton.OnCheckedChangeListener listener, Configs.ConfigKey config) {
        Context parent = viewContainer.getContext();
        View newView = ListViewItemBuilder.getPaddedBuilder(viewContainer.getContext())
                .setTitle(parent.getString(config.stringId))
                .setDescription(parent.getString(config.descriptionId))
                .setSwitchChecked(Configs.instance(parent).getBoolean(config))
                .setSwitchEvent(listener)
                .changeElementId(R.id.switchTypeEnabled, config.stringId)
                .build();
        viewContainer.addView(newView);
    }
}
